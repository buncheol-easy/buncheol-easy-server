package buncheoleasy.aidraft.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 엔진 계약(buncheol-easy-ai {@code app.py}·{@code models.py}·{@code assemble.py})을 HTTP 레벨에서 고정한다. 응답 본문 모양은 엔진
 * 코드가 실제로 내보내는 형태 그대로다 — 오류 매핑이 어긋나면 하루 횟수를 잘못 돌려주거나(돈) 사용자에게 엉뚱한 안내가 나간다.
 */
@DisplayName("AiWorkerClient HTTP 테스트")
class AiWorkerClientTest {

  private static final String TOKEN = "test-internal-token";
  private static final AiDraftSource URL_SOURCE =
      AiDraftSource.of("https://www.ktown4u.com/iteminfo?goods_no=1", null);

  private static final String SUCCESS_BODY =
      """
      {
        "draft": {
          "title": "투모로우바이투게더 미니 7집 분철",
          "description": "구성: 포토북 1종",
          "purchaseSite": "케이타운포유",
          "purchaseUrl": "https://www.ktown4u.com/iteminfo?goods_no=1",
          "groupName": "투모로우바이투게더 (TXT)",
          "listPrice": 14100,
          "priceCurrency": "KRW",
          "releaseDate": "2026-11-03",
          "priceNote": "정가 기준이에요"
        },
        "promo": {"tweetText": "홍보 트윗"},
        "images": {"candidates": ["iVBORw0KGgo=", "/9j/4AAQ"], "thumbnailIndex": 1},
        "meta": {
          "sourceType": "JSONLD",
          "sourceHost": "www.ktown4u.com",
          "groupMatchKeywords": ["TOMORROW X TOGETHER", "TXT"],
          "groupConfidence": "high",
          "aiCalls": [
            {"purpose": "group", "model": "claude-haiku-5-5", "inputTokens": 1200, "outputTokens": 80, "costUsd": 0.0004},
            {"purpose": "draft", "model": "claude-haiku-5-5", "inputTokens": 15000, "outputTokens": 900, "costUsd": 0.0013}
          ],
          "costUsd": 0.0017
        }
      }
      """;

  private HttpServer server;
  private AiWorkerClient client;

  private volatile int responseStatus;
  private volatile String responseBody;
  private volatile String responseContentType;
  private volatile long responseDelayMillis;
  private volatile String receivedMethod;
  private volatile String receivedPath;
  private volatile String receivedToken;
  private volatile String receivedContentType;
  private volatile String receivedBody;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::handle);
    server.start();
    client = new AiWorkerClient(properties("http://localhost:" + server.getAddress().getPort()));
    respond(200, SUCCESS_BODY);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private static AiDraftProperties properties(final String baseUrl) {
    return new AiDraftProperties(
        true, baseUrl, TOKEN, Duration.ofSeconds(1), Duration.ofMillis(500), 10, 30, 100, 4);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    receivedMethod = exchange.getRequestMethod();
    receivedPath = exchange.getRequestURI().getPath();
    receivedToken = exchange.getRequestHeaders().getFirst("X-Internal-Token");
    receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
    receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (responseDelayMillis > 0) {
      try {
        Thread.sleep(responseDelayMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", responseContentType);
    exchange.sendResponseHeaders(responseStatus, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    } catch (IOException ignored) {
      // 클라이언트가 읽기 시간 초과로 먼저 끊은 경우
    }
  }

  private void respond(final int status, final String body) {
    respond(status, body, "application/json");
  }

  private void respond(final int status, final String body, final String contentType) {
    responseStatus = status;
    responseBody = body;
    responseContentType = contentType;
  }

  private void assertFailsWith(final ErrorCode errorCode) {
    assertThatThrownBy(() -> client.createDraft(URL_SOURCE))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Nested
  @DisplayName("요청")
  class RequestTest {

    @Test
    void 내부_토큰_헤더를_실어_초안_경로로_주소만_보낸다() {
      client.createDraft(URL_SOURCE);

      assertThat(receivedMethod).isEqualTo("POST");
      assertThat(receivedPath).isEqualTo("/internal/drafts");
      assertThat(receivedToken).isEqualTo(TOKEN);
      assertThat(receivedContentType).startsWith("application/json");
      // 엔진은 둘 중 정확히 하나만 받는다 — 빈 쪽을 null 로 실어도 거절하지는 않지만 계약대로 아예 뺀다.
      assertThat(receivedBody)
          .isEqualTo("{\"url\":\"https://www.ktown4u.com/iteminfo?goods_no=1\"}");
    }

    @Test
    void 붙여_넣은_글은_rawText_로_보낸다() {
      client.createDraft(AiDraftSource.of(null, "앨범 14,100원"));

      assertThat(receivedBody).isEqualTo("{\"rawText\":\"앨범 14,100원\"}");
    }
  }

  @Nested
  @DisplayName("성공 응답")
  class SuccessTest {

    @Test
    void 초안과_사진과_그룹_검색어와_비용을_옮긴다() {
      AiWorkerDraft draft = client.createDraft(URL_SOURCE);

      assertThat(draft.title()).isEqualTo("투모로우바이투게더 미니 7집 분철");
      assertThat(draft.description()).isEqualTo("구성: 포토북 1종");
      assertThat(draft.purchaseSite()).isEqualTo("케이타운포유");
      assertThat(draft.purchaseUrl()).isEqualTo("https://www.ktown4u.com/iteminfo?goods_no=1");
      assertThat(draft.groupName()).isEqualTo("투모로우바이투게더 (TXT)");
      assertThat(draft.groupMatchKeywords()).containsExactly("TOMORROW X TOGETHER", "TXT");
      assertThat(draft.listPrice()).isEqualTo(14100L);
      assertThat(draft.priceCurrency()).isEqualTo("KRW");
      assertThat(draft.releaseDate()).isEqualTo("2026-11-03");
      assertThat(draft.images()).containsExactly("iVBORw0KGgo=", "/9j/4AAQ");
      assertThat(draft.thumbnailIndex()).isEqualTo(1);
      assertThat(draft.cost().costUsd()).isEqualByComparingTo(new BigDecimal("0.0017"));
      assertThat(draft.cost().inputTokens()).isEqualTo(16200);
      assertThat(draft.cost().outputTokens()).isEqualTo(980);
      assertThat(draft.cost().aiCallCount()).isEqualTo(2);
    }

    @Test
    void 단가를_모르는_모델이_끼어_비용이_null_이면_null_로_둔다() {
      respond(
          200,
          """
          {"draft": {"title": "t", "description": "d"},
           "images": {"candidates": [], "thumbnailIndex": 0},
           "meta": {"groupMatchKeywords": [], "costUsd": null,
                    "aiCalls": [{"purpose": "draft", "model": "x", "inputTokens": null, "outputTokens": null, "costUsd": null}]}}
          """);

      AiWorkerDraft draft = client.createDraft(URL_SOURCE);

      assertThat(draft.cost().costUsd()).isNull();
      assertThat(draft.cost().inputTokens()).isNull();
      assertThat(draft.cost().outputTokens()).isNull();
      assertThat(draft.cost().aiCallCount()).isEqualTo(1);
      assertThat(draft.groupName()).isNull();
      assertThat(draft.images()).isEmpty();
    }

    @Test
    void 성공_응답을_해석하지_못하면_AI_를_이미_부른_것으로_본다() {
      respond(200, "{\"draft\": ");

      assertFailsWith(ErrorCode.AI_DRAFT_GENERATION_FAILED);
    }
  }

  @Nested
  @DisplayName("오류 응답 매핑")
  class ErrorMappingTest {

    @Test
    void 입력_거절은_AI_001() {
      respond(400, "{\"code\":\"INVALID_INPUT\",\"message\":\"허용되지 않는 주소다: 169.254.169.254\"}");

      assertFailsWith(ErrorCode.AI_DRAFT_INVALID_INPUT);
    }

    @Test
    void 판매_페이지를_읽지_못하면_AI_002() {
      respond(422, "{\"code\":\"FETCH_FAILED\",\"message\":\"페이지를 읽지 못했다: 403\"}");

      assertFailsWith(ErrorCode.AI_DRAFT_FETCH_FAILED);
    }

    @Test
    void 요청_모양_검증_실패_422_는_읽기_실패가_아니라_계약_오류다() {
      // FastAPI 가 요청 본문 검증에 실패하면 code 없이 detail 로 422 를 준다 — 판매 페이지 안내를 띄우면 안 된다.
      respond(422, "{\"detail\":[{\"type\":\"string_type\",\"loc\":[\"body\",\"url\"]}]}");

      assertFailsWith(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }

    @Test
    void 생성_실패는_AI_004() {
      respond(502, "{\"code\":\"GENERATION_FAILED\",\"message\":\"초안 생성에 실패했다\"}");

      assertFailsWith(ErrorCode.AI_DRAFT_GENERATION_FAILED);
    }

    @Test
    void 엔진_동시_처리_상한_503_은_AI_005() {
      // uvicorn --limit-concurrency 는 JSON 이 아니라 평문으로 503 을 준다.
      respond(503, "Service Unavailable", "text/plain");

      assertFailsWith(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }

    @Test
    void 내부_토큰_거절_401_은_AI_005() {
      respond(401, "{\"code\":\"UNAUTHORIZED\",\"message\":\"내부 토큰이 없거나 틀리다\"}");

      assertFailsWith(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }

    @Test
    void 계약_밖_4xx_는_AI_까지_가지_않았으므로_AI_005() {
      respond(404, "{\"detail\":\"Not Found\"}");

      assertFailsWith(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }

    @Test
    void 처리_중_예상_못_한_5xx_는_AI_를_이미_불렀을_수_있어_AI_004() {
      respond(500, "Internal Server Error", "text/plain");

      assertFailsWith(ErrorCode.AI_DRAFT_GENERATION_FAILED);
    }
  }

  @Nested
  @DisplayName("연결·시간 초과")
  class IoFailureTest {

    @Test
    void 연결이_안_되면_엔진은_아무것도_안_했으므로_AI_005() throws IOException {
      int closedPort;
      try (ServerSocket socket = new ServerSocket(0)) {
        closedPort = socket.getLocalPort();
      }
      AiWorkerClient unreachable = new AiWorkerClient(properties("http://localhost:" + closedPort));

      assertThatThrownBy(() -> unreachable.createDraft(URL_SOURCE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }

    @Test
    void 응답_대기_시간을_넘기면_AI_를_이미_불렀을_수_있어_AI_004() {
      responseDelayMillis = 1500;

      assertFailsWith(ErrorCode.AI_DRAFT_GENERATION_FAILED);
    }
  }
}
