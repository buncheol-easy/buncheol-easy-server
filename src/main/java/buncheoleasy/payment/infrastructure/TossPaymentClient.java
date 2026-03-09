package buncheoleasy.payment.infrastructure;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class TossPaymentClient {

  private static final String UNKNOWN_ERROR_CODE = "UNKNOWN";
  private static final String UNPARSABLE_ERROR_CODE = "UNPARSABLE";
  private static final String UNKNOWN_PAYMENT_METHOD = "UNKNOWN";
  private static final String EMPTY_RESPONSE_BODY_MESSAGE = "토스 응답 본문 없음";
  private static final String ERROR_RESPONSE_PARSE_FAILED_MESSAGE = "토스 에러 응답 파싱 실패";
  private static final String CONFIRM_FAILED_MESSAGE = "토스 결제 승인 실패";

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final TossPaymentsProperties tossPaymentsProperties;

  public TossPaymentClient(
      final ObjectMapper objectMapper, final TossPaymentsProperties tossPaymentsProperties) {
    this.objectMapper = objectMapper;
    this.tossPaymentsProperties = tossPaymentsProperties;
    this.restClient =
        RestClient.builder()
            .requestFactory(createRequestFactory(tossPaymentsProperties))
            .defaultHeaders(
                headers -> {
                  headers.setBasicAuth(tossPaymentsProperties.secretKey(), "");
                  headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                  headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
            .build();
  }

  public TossConfirmResponse confirm(
      final String paymentKey, final String orderId, final long amount) {
    try {
      final TossConfirmApiResponse confirmResponse =
          restClient
              .post()
              .uri(tossPaymentsProperties.confirmUrl())
              .body(new TossConfirmRequest(paymentKey, orderId, amount))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, response) -> {
                    throw toConfirmFailure(response);
                  })
              .body(TossConfirmApiResponse.class);

      return toConfirmResponse(confirmResponse);
    } catch (TossConfirmRejectedException | TossConfirmUnavailableException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("토스 결제 승인 호출 중 통신 오류 발생", e);
      throw new TossConfirmUnavailableException(UNKNOWN_ERROR_CODE, CONFIRM_FAILED_MESSAGE, e);
    }
  }

  private SimpleClientHttpRequestFactory createRequestFactory(
      final TossPaymentsProperties tossPaymentsProperties) {
    final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(
        Math.toIntExact(tossPaymentsProperties.connectTimeout().toMillis()));
    requestFactory.setReadTimeout(Math.toIntExact(tossPaymentsProperties.readTimeout().toMillis()));
    return requestFactory;
  }

  private RuntimeException toConfirmFailure(final ClientHttpResponse response) throws IOException {
    final String responseBody =
        StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    final TossErrorResponse errorResponse = parseErrorResponse(responseBody);
    final int statusCode = response.getStatusCode().value();

    log.error(
        "토스 결제 승인 실패: status={}, code={}, message={}",
        statusCode,
        errorResponse.code(),
        errorResponse.message());

    if (response.getStatusCode().is4xxClientError()) {
      return new TossConfirmRejectedException(errorResponse.code(), errorResponse.message());
    }
    return new TossConfirmUnavailableException(errorResponse.code(), errorResponse.message());
  }

  private TossErrorResponse parseErrorResponse(final String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return new TossErrorResponse(UNKNOWN_ERROR_CODE, EMPTY_RESPONSE_BODY_MESSAGE);
    }

    try {
      final TossErrorResponse errorResponse =
          objectMapper.readValue(responseBody, TossErrorResponse.class);
      if (errorResponse == null) {
        return new TossErrorResponse(UNKNOWN_ERROR_CODE, ERROR_RESPONSE_PARSE_FAILED_MESSAGE);
      }
      final String code =
          errorResponse.code() == null || errorResponse.code().isBlank()
              ? UNKNOWN_ERROR_CODE
              : errorResponse.code();
      final String message =
          errorResponse.message() == null || errorResponse.message().isBlank()
              ? CONFIRM_FAILED_MESSAGE
              : errorResponse.message();
      return new TossErrorResponse(code, message);
    } catch (RuntimeException e) {
      return new TossErrorResponse(UNPARSABLE_ERROR_CODE, responseBody);
    }
  }

  private TossConfirmResponse toConfirmResponse(final TossConfirmApiResponse confirmResponse) {
    if (confirmResponse == null
        || confirmResponse.totalAmount() == null
        || confirmResponse.approvedAt() == null
        || confirmResponse.approvedAt().isBlank()) {
      log.error("토스 결제 승인 응답 필수값 누락: response={}", confirmResponse);
      throw new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_FAILED);
    }

    final String method =
        confirmResponse.method() == null || confirmResponse.method().isBlank()
            ? UNKNOWN_PAYMENT_METHOD
            : confirmResponse.method();

    return new TossConfirmResponse(
        confirmResponse.totalAmount(), method, confirmResponse.approvedAt());
  }

  private record TossConfirmRequest(String paymentKey, String orderId, long amount) {}

  private record TossConfirmApiResponse(Long totalAmount, String method, String approvedAt) {}

  private record TossErrorResponse(String code, String message) {}

  public record TossConfirmResponse(long totalAmount, String method, String approvedAt) {}

  public static class TossConfirmRejectedException extends RuntimeException {

    private final String errorCode;

    public TossConfirmRejectedException(final String errorCode, final String message) {
      super(message);
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  public static class TossConfirmUnavailableException extends RuntimeException {

    private final String errorCode;

    public TossConfirmUnavailableException(final String errorCode, final String message) {
      super(message);
      this.errorCode = errorCode;
    }

    public TossConfirmUnavailableException(
        final String errorCode, final String message, final Throwable cause) {
      super(message, cause);
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }
}
