package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 실제 HTTP 경로 검증. CU 기록과 NOT_FOUND 응답 본문은 2026-10-26 Delivery Tracker 운영 API 에 이 클라이언트와 같은 쿼리로 받은
 * 실제 응답이다.
 */
@DisplayName("DeliveryTrackerClient HTTP 테스트")
class DeliveryTrackerClientTest {

  private static final String CARRIER_CU = "kr.cupost";
  private static final String TRACKING_NUMBER = "000000000000";

  private HttpServer server;
  private DeliveryTrackerClient client;

  private volatile String responseBody;
  private volatile String receivedAuthorization;
  private volatile String receivedBody;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::handle);
    server.start();
    client =
        new DeliveryTrackerClient(
            new DeliveryTrackerProperties(
                "http://localhost:" + server.getAddress().getPort() + "/graphql",
                "client-id",
                "client-secret",
                "https://example.com/callback",
                "webhook-token",
                Duration.ofHours(48),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofMillis(1)));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
    receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  @Nested
  @DisplayName("최근 추적 기록 조회")
  class FindRecentEvents {

    @Test
    void 기록을_오래된_순서_그대로_코드_원문_시각으로_돌려준다() {
      responseBody =
          """
          {"data":{"track":{"events":{"edges":[
          {"node":{"time":"2026-09-23T15:58:00.000+09:00","status":{"code":"UNKNOWN","name":"접수완료"}}},
          {"node":{"time":"2026-09-23T17:05:12.000+09:00","status":{"code":"AT_PICKUP","name":"집하완료"}}},
          {"node":{"time":"2026-09-27T20:17:15.000+09:00","status":{"code":"IN_TRANSIT","name":"도착"}}},
          {"node":{"time":"2026-09-27T20:40:02.000+09:00","status":{"code":"IN_TRANSIT","name":"발송"}}},
          {"node":{"time":"2026-09-28T05:20:31.000+09:00","status":{"code":"IN_TRANSIT","name":"도착"}}},
          {"node":{"time":"2026-09-28T05:21:03.000+09:00","status":{"code":"IN_TRANSIT","name":"발송"}}},
          {"node":{"time":"2026-09-28T08:43:17.000+09:00","status":{"code":"IN_TRANSIT","name":"도착"}}},
          {"node":{"time":"2026-09-28T08:44:32.000+09:00","status":{"code":"OUT_FOR_DELIVERY","name":"배달전"}}},
          {"node":{"time":"2026-09-28T11:46:00.000+09:00","status":{"code":"UNKNOWN","name":"점포도착"}}},
          {"node":{"time":"2026-09-28T11:46:02.000+09:00","status":{"code":"DELIVERED","name":"배달완료"}}},
          {"node":{"time":"2026-09-28T20:41:00.000+09:00","status":{"code":"UNKNOWN","name":"수령완료"}}}
          ]}}}}
          """;

      List<TrackEvent> events = client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER);

      assertThat(events).hasSize(11);
      assertThat(events.subList(8, 11))
          .containsExactly(
              new TrackEvent(
                  "UNKNOWN", "점포도착", OffsetDateTime.parse("2026-09-28T11:46:00+09:00").toInstant()),
              new TrackEvent(
                  "DELIVERED", "배달완료", OffsetDateTime.parse("2026-09-28T11:46:02+09:00").toInstant()),
              new TrackEvent(
                  "UNKNOWN", "수령완료", OffsetDateTime.parse("2026-09-28T20:41:00+09:00").toInstant()));
    }

    @Test
    void lastEvent_가_아니라_최근_기록을_인증_헤더와_함께_요청한다() {
      responseBody = """
          {"data":{"track":{"events":{"edges":[]}}}}
          """;

      client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER);

      assertThat(receivedAuthorization).isEqualTo("TRACKQL-API-KEY client-id:client-secret");
      assertThat(receivedBody).contains("events(last: 20)").doesNotContain("lastEvent");
      assertThat(receivedBody).contains(CARRIER_CU).contains(TRACKING_NUMBER);
    }

    @Test
    void 캐리어에_없는_운송장_NOT_FOUND_는_빈_목록이다() {
      responseBody =
          """
          {"errors":[{"message":"배송조회 결과가 없습니다.","locations":[{"line":2,"column":3}],
          "path":["track"],"extensions":{"code":"NOT_FOUND"}}],"data":{"track":null}}
          """;

      assertThat(client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER)).isEmpty();
    }

    @Test
    void 기록이_비어_있으면_빈_목록이다() {
      responseBody = """
          {"data":{"track":{"events":{"edges":[]}}}}
          """;

      assertThat(client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER)).isEmpty();
    }

    @Test
    void 비어_있는_edge_와_node_는_건너뛴다() {
      responseBody =
          """
          {"data":{"track":{"events":{"edges":[
          null,
          {"node":null},
          {"node":{"time":"2026-09-28T11:46:00.000+09:00","status":{"code":"UNKNOWN","name":"점포도착"}}}
          ]}}}}
          """;

      assertThat(client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER))
          .containsExactly(
              new TrackEvent(
                  "UNKNOWN", "점포도착", OffsetDateTime.parse("2026-09-28T11:46:00+09:00").toInstant()));
    }

    @Test
    void 시각_형식이_다르면_시각만_비우고_이벤트는_돌려준다() {
      responseBody =
          """
          {"data":{"track":{"events":{"edges":[
          {"node":{"time":"2026-09-28 11:46","status":{"code":"UNKNOWN","name":"점포도착"}}}
          ]}}}}
          """;

      assertThat(client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER))
          .containsExactly(new TrackEvent("UNKNOWN", "점포도착", null));
    }

    @Test
    void NOT_FOUND_가_아닌_오류는_예외로_올린다() {
      responseBody =
          """
          {"errors":[{"message":"Please try again in a few minutes. (Internal error)",
          "path":["track"],"extensions":{"code":"INTERNAL"}}],"data":{"track":null}}
          """;

      assertThatThrownBy(() -> client.findRecentEvents(CARRIER_CU, TRACKING_NUMBER))
          .isInstanceOf(DeliveryTrackerException.class);
    }
  }
}
