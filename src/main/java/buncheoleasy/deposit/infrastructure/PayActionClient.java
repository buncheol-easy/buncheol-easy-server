package buncheoleasy.deposit.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 페이액션 주문 API 클라이언트. 참여가 생기면 "입금자명 + 입금액"을 주문으로 등록해두고, 페이액션이 은행 입출금 통지에서 같은 값을 발견하면 매칭 웹훅을 보낸다.
 *
 * <p>주의 두 가지가 있다. (1) 페이액션은 업무 검증 실패를 HTTP 200 + {@code status: "error"} 로 돌려주므로 상태코드만 보고 성공을 판단하면
 * 안 된다. (2) 주문이 입금보다 먼저 도달해야 매칭되고 {@code order_date} 가 미래면 매칭되지 않으므로, 참여 커밋 직후 지체 없이 등록해야 한다.
 */
@Slf4j
@Component
public class PayActionClient {

  /** 페이액션이 요구하는 ISO-8601 형식(오프셋 포함). 매칭 기준이 한국 시간이라 KST 로 보낸다. */
  private static final DateTimeFormatter ORDER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.of("Asia/Seoul"));

  private static final String SUCCESS_STATUS = "success";

  private final RestClient restClient;
  private final PayActionProperties properties;

  public PayActionClient(final PayActionProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder().requestFactory(createRequestFactory(properties)).build();
  }

  /** 키가 주입된 환경인지. 호출자가 미설정 환경에서 조회까지 건너뛸 수 있게 노출한다. */
  public boolean isEnabled() {
    return properties.outboundEnabled();
  }

  /**
   * 매칭 대기 주문 등록. {@code depositorName} 앞뒤 공백은 매칭 실패 사유라 제거해서 보낸다.
   *
   * @param orderNumber 참여 ID (웹훅으로 되돌아오는 식별자)
   * @param amount 입금해야 할 총액 (멤버 금액 + 배송비)
   * @param depositorName 입금자명 — 참여 시점 환불계좌 예금주명
   */
  public void registerOrder(
      final Long orderNumber,
      final long amount,
      final String depositorName,
      final Instant orderedAt,
      final Instant dueAt) {
    if (!isEnabled()) {
      log.debug("페이액션 미설정 - 주문 등록 건너뜀 - participationId={}", orderNumber);
      return;
    }
    String name = depositorName == null ? "" : depositorName.trim();
    post(
        "/order",
        new OrderRequest(
            String.valueOf(orderNumber),
            amount,
            ORDER_DATE_FORMAT.format(orderedAt),
            ORDER_DATE_FORMAT.format(dueAt),
            name,
            name),
        orderNumber);
  }

  /** 매칭 대기 해제. 참여가 취소·만료돼 더는 입금을 기다리지 않을 때 호출한다. */
  public void excludeOrder(final Long orderNumber) {
    if (!isEnabled()) {
      log.debug("페이액션 미설정 - 매칭제외 건너뜀 - participationId={}", orderNumber);
      return;
    }
    post("/order-exclude", new OrderExcludeRequest(String.valueOf(orderNumber)), orderNumber);
  }

  private void post(final String path, final Object body, final Long orderNumber) {
    final PayActionResponse response;
    try {
      response =
          restClient
              .post()
              .uri(properties.baseUrl() + path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-api-key", properties.apiKey())
              .header("x-mall-id", properties.mallId())
              .body(body)
              .retrieve()
              .body(PayActionResponse.class);
    } catch (RestClientException e) {
      log.error("페이액션 호출 통신 오류 - path={} participationId={}", path, orderNumber, e);
      throw new PayActionSendException("페이액션 호출 통신 오류: " + path, e);
    }
    verify(response, path, orderNumber);
  }

  /** 업무 검증 실패가 HTTP 200 본문으로 오므로 status 를 반드시 확인한다. */
  private void verify(
      final PayActionResponse response, final String path, final Long orderNumber) {
    if (response != null && SUCCESS_STATUS.equals(response.status())) {
      return;
    }
    String message = response == null ? "응답 없음" : String.valueOf(response.response());
    log.error("페이액션 호출 실패 - path={} participationId={} response={}", path, orderNumber, message);
    throw new PayActionSendException("페이액션 호출 실패: " + path + " - " + message);
  }

  private SimpleClientHttpRequestFactory createRequestFactory(
      final PayActionProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  /**
   * 주문 등록 요청. {@code billingName}(입금자명)이 매칭 판단 항목이고 {@code ordererName}(주문자명)은 표시용인데, 페이액션이 둘 다
   * 필수로 요구해 같은 값을 보낸다.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record OrderRequest(
      @JsonProperty("order_number") String orderNumber,
      @JsonProperty("order_amount") long orderAmount,
      @JsonProperty("order_date") String orderDate,
      @JsonProperty("auto_cancel_date") String autoCancelDate,
      @JsonProperty("billing_name") String billingName,
      @JsonProperty("orderer_name") String ordererName) {}

  private record OrderExcludeRequest(@JsonProperty("order_number") String orderNumber) {}

  private record PayActionResponse(String status, Object response) {}
}
