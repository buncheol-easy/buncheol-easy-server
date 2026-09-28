package buncheoleasy.deposit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 실제 HTTP 경로 검증. 응답 본문은 2026-09-28 페이액션 운영 API 에서 받은 실제 응답이다 — 엔드포인트 폐기(410)가 로그 한 줄로만 남아
 * 자동 취소가 조용히 끊겼던 전례가 있어, 경로·헤더·본문 유무와 응답 판정을 HTTP 레벨에서 고정한다.
 */
@DisplayName("PayActionClient HTTP 테스트")
class PayActionClientTest {

  private static final String API_KEY = "test-api-key";
  private static final String MALL_ID = "test-mall-id";

  private HttpServer server;
  private PayActionClient client;

  private volatile int responseStatus;
  private volatile String responseBody;
  private volatile String receivedMethod;
  private volatile String receivedPath;
  private volatile String receivedApiKey;
  private volatile String receivedMallId;
  private volatile String receivedContentType;
  private volatile String receivedBody;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::handle);
    server.start();
    client =
        new PayActionClient(
            new PayActionProperties(
                "http://localhost:" + server.getAddress().getPort(),
                API_KEY,
                MALL_ID,
                "webhook-key",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    receivedMethod = exchange.getRequestMethod();
    receivedPath = exchange.getRequestURI().getPath();
    receivedApiKey = exchange.getRequestHeaders().getFirst("x-api-key");
    receivedMallId = exchange.getRequestHeaders().getFirst("x-mall-id");
    receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
    receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(responseStatus, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private void respond(final int status, final String body) {
    responseStatus = status;
    responseBody = body;
  }

  @Nested
  @DisplayName("주문 취소")
  class CancelOrder {

    @Test
    void 주문번호_경로로_본문_없이_인증_헤더를_실어_호출한다() {
      respond(
          200,
          """
          {"status":"success","order":{"order_number":"68","cancellation_status":"cancelled",
          "cancel_amount":1,"cumulative_cancelled_amount":1,"remaining_amount":0}}
          """);

      client.cancelOrder(68L);

      assertThat(receivedMethod).isEqualTo("POST");
      assertThat(receivedPath).isEqualTo("/orders/68/cancel");
      assertThat(receivedApiKey).isEqualTo(API_KEY);
      assertThat(receivedMallId).isEqualTo(MALL_ID);
      assertThat(receivedBody).isEmpty();
      // JDK HttpURLConnection 이 본문 없는 POST 에도 붙이는 기본값. 운영 API 가 이 헤더로도 취소를 처리함을 확인했다.
      assertThat(receivedContentType).isEqualTo("application/x-www-form-urlencoded");
    }

    // 등록을 건너뛴 참여(0원·계좌 없음·등록 실패)도 만료·취소 시 호출되므로 정상 경로다.
    @Test
    void 주문이_없으면_예외_없이_넘어간다() {
      respond(
          404,
          """
          {"status":"error","error":{"code":"ORDER_NOT_FOUND","message":"취소할 주문을 찾을 수 없습니다."}}
          """);

      assertThatCode(() -> client.cancelOrder(999L)).doesNotThrowAnyException();
    }

    @Test
    void 코드가_다른_404_는_예외를_던진다() {
      respond(404, "<html>Not Found</html>");

      assertThatThrownBy(() -> client.cancelOrder(68L))
          .isInstanceOf(PayActionSendException.class)
          .hasMessageContaining("404");
    }

    @Test
    void 엔드포인트가_폐기되면_예외를_던진다() {
      respond(
          410,
          """
          {"status":"error","response":{"message":"종료된 API입니다."}}
          """);

      assertThatThrownBy(() -> client.cancelOrder(68L))
          .isInstanceOf(PayActionSendException.class)
          .hasMessageContaining("410");
    }

    @Test
    void HTTP_200_이어도_status_가_error_면_예외를_던진다() {
      respond(200, """
          {"status":"error","error":{"code":"UNKNOWN","message":"실패"}}
          """);

      assertThatThrownBy(() -> client.cancelOrder(68L))
          .isInstanceOf(PayActionSendException.class)
          .hasMessageContaining("UNKNOWN");
    }
  }

  @Nested
  @DisplayName("주문 등록")
  class RegisterOrder {

    @Test
    void JSON_본문으로_주문을_등록한다() {
      respond(200, """
          {"status":"success","response":{}}
          """);

      client.registerOrder(
          68L,
          15000L,
          " 홍길동 ",
          Instant.parse("2026-09-28T13:04:11Z"),
          Instant.parse("2026-09-28T13:34:11Z"));

      assertThat(receivedPath).isEqualTo("/order");
      assertThat(receivedContentType).startsWith("application/json");
      assertThat(receivedBody)
          .contains("\"order_number\":\"68\"")
          .contains("\"order_amount\":15000")
          .contains("\"order_date\":\"2026-09-28T22:04:11+09:00\"")
          .contains("\"auto_cancel_date\":\"2026-09-28T22:34:11+09:00\"")
          .contains("\"billing_name\":\"홍길동\"");
    }
  }
}
