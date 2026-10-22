package buncheoleasy.aidraft.infrastructure;

import buncheoleasy.aidraft.domain.AiDraftCost;
import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 엔진(buncheol-easy-ai) 클라이언트 — {@code POST /internal/drafts}. 엔진은 같은 박스의 격리 컨테이너라 내부 토큰 헤더 하나로만
 * 인증한다. 토큰은 로그에 남기지 않는다.
 *
 * <p>실패는 응답 오류 코드(AI-00x)를 실은 {@link BusinessException} 으로 바꾼다. 하루 횟수를 돌려줄지는 부르는 쪽이 코드로 고른다 — {@code
 * AI-004} 만 엔진이 AI 를 이미 불렀을 수 있다.
 */
@Slf4j
@Component
public class AiWorkerClient {

  private static final String DRAFTS_PATH = "/internal/drafts";
  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
  private static final String INVALID_INPUT = "INVALID_INPUT";
  private static final String FETCH_FAILED = "FETCH_FAILED";
  private static final int MAX_LOGGED_BODY_LENGTH = 500;

  private final RestClient restClient;
  private final AiDraftProperties properties;

  public AiWorkerClient(final AiDraftProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder().requestFactory(createRequestFactory(properties)).build();
  }

  public AiWorkerDraft createDraft(final AiDraftSource source) {
    final DraftResponse response;
    try {
      response =
          restClient
              .post()
              .uri(properties.workerBaseUrl() + DRAFTS_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .header(INTERNAL_TOKEN_HEADER, properties.workerInternalToken())
              .body(new DraftRequest(source.url(), source.rawText()))
              .retrieve()
              .body(DraftResponse.class);
    } catch (RestClientResponseException e) {
      throw fromErrorResponse(e);
    } catch (RestClientException e) {
      throw fromTransportFailure(e);
    }
    if (response == null || response.draft() == null) {
      log.error("AI 엔진 응답에 초안이 없음");
      throw new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED);
    }
    return toDraft(response);
  }

  /** 엔진 오류 계약(app.py): 400 INVALID_INPUT · 422 FETCH_FAILED · 502 GENERATION_FAILED · 401 토큰 · 503 동시 처리 상한. */
  private BusinessException fromErrorResponse(final RestClientResponseException e) {
    int status = e.getStatusCode().value();
    String code = errorCodeOf(e);
    String body = truncate(e.getResponseBodyAsString());
    if (status == 401) {
      log.error(
          "AI 엔진이 내부 토큰을 거절 - AI_WORKER_INTERNAL_TOKEN 이 엔진 INTERNAL_TOKEN 과 같은지 확인 - body={}",
          body);
      return new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE, e);
    }
    if (status == 400 && INVALID_INPUT.equals(code)) {
      log.info("AI 엔진이 입력을 거절 - body={}", body);
      return new BusinessException(ErrorCode.AI_DRAFT_INVALID_INPUT, e);
    }
    if (status == 422 && FETCH_FAILED.equals(code)) {
      log.info("AI 엔진이 판매 페이지를 읽지 못함 - body={}", body);
      return new BusinessException(ErrorCode.AI_DRAFT_FETCH_FAILED, e);
    }
    if (status == 502) {
      log.warn("AI 엔진 생성 실패 - body={}", body);
      return new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED, e);
    }
    if (status == 503) {
      log.warn("AI 엔진이 동시 처리 상한에 걸림 - body={}", body);
      return new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE, e);
    }
    if (e.getStatusCode().is4xxClientError()) {
      // 경로·요청 모양이 엔진과 어긋났다 — 엔진은 AI 까지 가지 않았다.
      log.error("AI 엔진 계약 밖 응답 - status={} body={}", status, body, e);
      return new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE, e);
    }
    // 처리 중 예상 못 한 오류(500 등) — AI 를 이미 불렀을 수 있다.
    log.error("AI 엔진 오류 응답 - status={} body={}", status, body, e);
    return new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED, e);
  }

  /**
   * 응답 상태를 받지 못한 실패. 연결 자체가 안 됐으면 엔진은 아무 일도 하지 않았다(AI-005). 연결된 뒤의 실패 — 응답 대기 시간 초과·끊김·본문을 읽지
   * 못함 — 는 AI 를 이미 불렀을 수 있다(AI-004).
   *
   * <p>예외 타입이 아니라 가장 안쪽 원인으로 가른다. JDK 연결(HttpURLConnection)은 읽기 시간 초과를 {@code ResourceAccessException} 이
   * 아니라 본문 추출 실패로 감싸 올리기도 한다. 연결 시간 초과와 읽기 시간 초과는 같은 예외 타입이라 JDK 메시지("Connect timed out" / "Read
   * timed out")로 가른다.
   */
  private BusinessException fromTransportFailure(final RestClientException e) {
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
    if (isNotConnected(cause)) {
      log.error("AI 엔진에 연결하지 못함 - baseUrl={}", properties.workerBaseUrl(), e);
      return new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE, e);
    }
    if (cause instanceof SocketTimeoutException) {
      log.error("AI 엔진 응답 대기 시간 초과 - readTimeout={}", properties.readTimeout(), e);
      return new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED, e);
    }
    log.error("AI 엔진 응답을 끝까지 받지 못했거나 해석하지 못함", e);
    return new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED, e);
  }

  private static boolean isNotConnected(final Throwable cause) {
    return cause instanceof ConnectException
        || cause instanceof UnknownHostException
        || cause instanceof NoRouteToHostException
        || cause instanceof SocketTimeoutException timeout
            && timeout.getMessage() != null
            && timeout.getMessage().toLowerCase(Locale.ROOT).contains("connect");
  }

  /** 본문이 엔진 오류 모양이 아니면(게이트웨이 HTML·검증 오류 등) 코드 없이 상태로만 판단한다. */
  private static String errorCodeOf(final RestClientResponseException e) {
    try {
      ErrorBody body = e.getResponseBodyAs(ErrorBody.class);
      return body == null ? null : body.code();
    } catch (RestClientException parseFailure) {
      return null;
    }
  }

  private static AiWorkerDraft toDraft(final DraftResponse response) {
    Draft draft = response.draft();
    Images images = response.images() == null ? new Images(List.of(), 0) : response.images();
    Meta meta = response.meta() == null ? new Meta(List.of(), null, List.of()) : response.meta();
    List<AiCall> calls = meta.aiCalls() == null ? List.of() : meta.aiCalls();
    return new AiWorkerDraft(
        draft.title(),
        draft.description(),
        draft.purchaseSite(),
        draft.purchaseUrl(),
        draft.groupName(),
        meta.groupMatchKeywords() == null ? List.of() : meta.groupMatchKeywords(),
        draft.listPrice(),
        draft.priceCurrency(),
        draft.releaseDate(),
        images.candidates() == null ? List.of() : images.candidates(),
        images.thumbnailIndex(),
        new AiDraftCost(
            meta.costUsd(),
            sumOrNull(calls, AiCall::inputTokens),
            sumOrNull(calls, AiCall::outputTokens),
            calls.size()));
  }

  private static Integer sumOrNull(
      final List<AiCall> calls, final Function<AiCall, Integer> tokens) {
    List<Integer> known = calls.stream().map(tokens).filter(Objects::nonNull).toList();
    return known.isEmpty() ? null : known.stream().mapToInt(Integer::intValue).sum();
  }

  private static String truncate(final String value) {
    return value.length() <= MAX_LOGGED_BODY_LENGTH
        ? value
        : value.substring(0, MAX_LOGGED_BODY_LENGTH) + "...";
  }

  private static SimpleClientHttpRequestFactory createRequestFactory(
      final AiDraftProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  /** 엔진은 둘 중 하나만 받는다 — 비어 있는 쪽은 싣지 않는다. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record DraftRequest(String url, String rawText) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DraftResponse(Draft draft, Images images, Meta meta) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Draft(
      String title,
      String description,
      String purchaseSite,
      String purchaseUrl,
      String groupName,
      Long listPrice,
      String priceCurrency,
      String releaseDate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Images(List<String> candidates, int thumbnailIndex) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Meta(List<String> groupMatchKeywords, BigDecimal costUsd, List<AiCall> aiCalls) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AiCall(Integer inputTokens, Integer outputTokens) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ErrorBody(String code) {}
}
