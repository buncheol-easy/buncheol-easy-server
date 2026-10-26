package buncheoleasy.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushMessage;
import buncheoleasy.push.domain.PushPlatform;
import buncheoleasy.push.infrastructure.ApnsTestKeys.MutableClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ApnsClient 테스트")
class ApnsClientTest {

  private static final String TEAM_ID = "TEAM000001";
  private static final String KEY_ID = "KEY0000001";
  private static final String TOPIC = "com.example.app";
  private static final String TOKEN = "ab".repeat(32);
  private static final PushMessage MESSAGE =
      new PushMessage("입금 확인 안내", "입금이 확인되었어요!\n\n▶ 분철명: 아이브 앨범", "/profile/bids");

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  private KeyPair keyPair;
  private MutableClock clock;
  private FakeTransport transport;

  @BeforeEach
  void setUp() {
    keyPair = ApnsTestKeys.newKeyPair();
    clock = new MutableClock(Instant.parse("2026-10-27T00:00:00Z"));
    transport = new FakeTransport();
  }

  @AfterEach
  void clearInterrupt() {
    // InterruptedException 경로가 남긴 인터럽트 표시가 다음 테스트로 새지 않게.
    Thread.interrupted();
  }

  private ApnsProperties properties(
      final String teamId, final String keyId, final String privateKey, final String topic) {
    return new ApnsProperties(
        teamId, keyId, privateKey, topic, Duration.ofSeconds(3), Duration.ofSeconds(5));
  }

  private ApnsClient enabledClient() {
    return new ApnsClient(
        properties(TEAM_ID, KEY_ID, ApnsTestKeys.pem(keyPair.getPrivate()), TOPIC),
        transport,
        objectMapper,
        clock);
  }

  private static PushDevice device(final PushEnvironment environment) {
    return PushDevice.create(7L, PushPlatform.IOS, TOKEN, environment);
  }

  @Nested
  @DisplayName("꺼짐")
  class DisabledTest {

    @ParameterizedTest
    @ValueSource(strings = {"teamId", "keyId", "privateKey", "topic"})
    void 설정이_하나라도_비면_꺼지고_보내지_않는다(final String blank) {
      String pem = ApnsTestKeys.pem(keyPair.getPrivate());
      ApnsClient client =
          new ApnsClient(
              properties(
                  blank.equals("teamId") ? "" : TEAM_ID,
                  blank.equals("keyId") ? " " : KEY_ID,
                  blank.equals("privateKey") ? null : pem,
                  blank.equals("topic") ? "" : TOPIC),
              transport,
              objectMapper,
              clock);

      assertThat(client.isEnabled()).isFalse();
      assertThat(client.send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.DISABLED);
      assertThat(transport.requests).isEmpty();
    }

    @Test
    void 키_본문을_읽지_못하면_꺼진다() {
      ApnsClient client =
          new ApnsClient(
              properties(TEAM_ID, KEY_ID, "-----BEGIN PRIVATE KEY-----\nabcd\n", TOPIC),
              transport,
              objectMapper,
              clock);

      assertThat(client.isEnabled()).isFalse();
      assertThat(client.send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.DISABLED);
      assertThat(transport.requests).isEmpty();
    }

    @Test
    void P_256_이_아닌_키는_서명할_수_없어_꺼진다() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp384r1"));
      String p384 = ApnsTestKeys.pem(generator.generateKeyPair().getPrivate());

      ApnsClient client =
          new ApnsClient(properties(TEAM_ID, KEY_ID, p384, TOPIC), transport, objectMapper, clock);

      assertThat(client.isEnabled()).isFalse();
      assertThat(client.send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.DISABLED);
      assertThat(transport.requests).isEmpty();
    }

    @Test
    void 비어_있는_키_이름만_알려_준다() {
      ApnsProperties properties = properties(TEAM_ID, "", null, TOPIC);

      assertThat(properties.configured()).isFalse();
      assertThat(properties.missingEnvKeys()).containsExactly("APNS_KEY_ID", "APNS_PRIVATE_KEY");
      assertThat(properties(TEAM_ID, KEY_ID, "secret-pem-body", TOPIC).toString())
          .doesNotContain("secret-pem-body");
    }
  }

  @Nested
  @DisplayName("요청 모양")
  class RequestTest {

    @Test
    void 개발_빌드_기기는_sandbox_서버로_보낸다() {
      enabledClient().send(device(PushEnvironment.SANDBOX), MESSAGE);

      assertThat(transport.only().uri())
          .isEqualTo(URI.create("https://api.sandbox.push.apple.com/3/device/" + TOKEN));
    }

    @Test
    void 스토어_빌드_기기는_운영_서버로_보낸다() {
      enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE);

      assertThat(transport.only().uri())
          .isEqualTo(URI.create("https://api.push.apple.com/3/device/" + TOKEN));
    }

    @Test
    void 제공자_토큰과_애플_필수_헤더를_싣는다() {
      enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE);

      Map<String, String> headers = transport.only().headers();
      assertThat(headers)
          .containsEntry("apns-topic", TOPIC)
          .containsEntry("apns-push-type", "alert")
          .containsEntry("apns-priority", "10");
      assertThat(headers.get("authorization")).startsWith("bearer ");
      Jws<Claims> jws = verify(headers.get("authorization").substring("bearer ".length()));
      assertThat(jws.getHeader().getKeyId()).isEqualTo(KEY_ID);
      assertThat(jws.getPayload().getIssuer()).isEqualTo(TEAM_ID);
    }

    @Test
    void 본문은_aps_alert_제목_본문_소리와_이동_경로다() {
      enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE);

      JsonNode payload = objectMapper.readTree(transport.only().body());
      assertThat(payload.get("aps").get("alert").get("title").asString()).isEqualTo("입금 확인 안내");
      assertThat(payload.get("aps").get("alert").get("body").asString())
          .isEqualTo("입금이 확인되었어요!\n\n▶ 분철명: 아이브 앨범");
      assertThat(payload.get("aps").get("sound").asString()).isEqualTo("default");
      assertThat(payload.get("linkPath").asString()).isEqualTo("/profile/bids");
    }

    @Test
    void 이동_경로가_없으면_linkPath_를_싣지_않는다() {
      enabledClient()
          .send(device(PushEnvironment.PRODUCTION), new PushMessage("수령 안내", "본문", null));

      JsonNode payload = objectMapper.readTree(transport.only().body());
      assertThat(payload.has("linkPath")).isFalse();
    }

    @Test
    void 본문이_500자를_넘으면_500자에서_자르고_말줄임표를_붙인다() {
      enabledClient()
          .send(device(PushEnvironment.PRODUCTION), new PushMessage("제목", "가".repeat(501), null));

      String body =
          objectMapper.readTree(transport.only().body()).get("aps").get("alert").get("body").asString();
      assertThat(body).isEqualTo("가".repeat(500) + "…");
    }

    @Test
    void 본문이_500자_이하면_자르지_않는다() {
      assertThat(ApnsClient.truncate("가".repeat(500))).isEqualTo("가".repeat(500));
    }

    @Test
    void 이모지는_반으로_가르지_않는다() {
      String truncated = ApnsClient.truncate("😀".repeat(501));

      assertThat(truncated).isEqualTo("😀".repeat(500) + "…");
      assertThat(Character.isHighSurrogate(truncated.charAt(truncated.length() - 2))).isFalse();
    }

    @Test
    void 최대_길이_알림도_애플_상한_4KB_를_넘지_않는다() {
      // 수신함 알림 상한: 제목 200자, 경로 500자. 본문은 잘린 뒤 500자 + 말줄임표.
      PushMessage longest =
          new PushMessage("😀".repeat(100), "😀".repeat(600), "/" + "a".repeat(499));

      enabledClient().send(device(PushEnvironment.PRODUCTION), longest);

      assertThat(transport.only().body().getBytes(StandardCharsets.UTF_8))
          .hasSizeLessThanOrEqualTo(4096);
    }
  }

  @Nested
  @DisplayName("응답 처리")
  class ResponseTest {

    @Test
    void 성공이면_SENT() {
      transport.respond(new ApnsTransport.ApnsResponse(200, ""));

      assertThat(enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.SENT);
    }

    @ParameterizedTest
    @CsvSource({
      "410, '{\"reason\":\"Unregistered\",\"timestamp\":1700000000000}'",
      "400, '{\"reason\":\"BadDeviceToken\"}'"
    })
    void 애플이_토큰을_무효라고_답하면_TOKEN_INVALID(final int status, final String body) {
      transport.respond(new ApnsTransport.ApnsResponse(status, body));

      assertThat(enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.TOKEN_INVALID);
    }

    @ParameterizedTest
    @CsvSource({
      "400, '{\"reason\":\"PayloadTooLarge\"}'",
      "400, '{\"reason\":\"DeviceTokenNotForTopic\"}'",
      "403, '{\"reason\":\"InvalidProviderToken\"}'",
      "429, '{\"reason\":\"TooManyRequests\"}'",
      "500, '{\"reason\":\"InternalServerError\"}'",
      "503, 'not json'",
      "400, ''"
    })
    void 그_밖의_실패는_FAILED_이고_토큰을_지우지_않는다(final int status, final String body) {
      transport.respond(new ApnsTransport.ApnsResponse(status, body));

      assertThat(enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.FAILED);
    }

    @Test
    void 전송_오류는_FAILED_이고_예외를_던지지_않는다() {
      ApnsClient client = enabledClient();
      transport.fail(new IOException("connection reset"));
      transport.fail(new IllegalStateException("unexpected"));

      assertThat(client.send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.FAILED);
      assertThat(client.send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.FAILED);
    }

    @Test
    void 중단되면_FAILED_이고_인터럽트_표시를_되살린다() {
      transport.fail(new InterruptedException());

      assertThat(enabledClient().send(device(PushEnvironment.PRODUCTION), MESSAGE))
          .isEqualTo(ApnsSendResult.FAILED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void 제공자_토큰이_만료로_거절되면_다음_발송은_새_토큰으로_보낸다() {
      ApnsClient client = enabledClient();
      transport.respond(new ApnsTransport.ApnsResponse(403, "{\"reason\":\"ExpiredProviderToken\"}"));
      client.send(device(PushEnvironment.PRODUCTION), MESSAGE);
      clock.advance(Duration.ofSeconds(1));

      client.send(device(PushEnvironment.PRODUCTION), MESSAGE);

      assertThat(transport.requests.get(1).headers().get("authorization"))
          .isNotEqualTo(transport.requests.get(0).headers().get("authorization"));
    }

    @Test
    void 다른_실패에는_제공자_토큰을_다시_쓴다() {
      ApnsClient client = enabledClient();
      transport.respond(new ApnsTransport.ApnsResponse(500, "{\"reason\":\"InternalServerError\"}"));
      client.send(device(PushEnvironment.PRODUCTION), MESSAGE);
      clock.advance(Duration.ofSeconds(1));

      client.send(device(PushEnvironment.PRODUCTION), MESSAGE);

      assertThat(transport.requests.get(1).headers().get("authorization"))
          .isEqualTo(transport.requests.get(0).headers().get("authorization"));
    }
  }

  private Jws<Claims> verify(final String token) {
    return Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token);
  }

  private record Request(URI uri, Map<String, String> headers, String body) {}

  /** 애플 서버 대신 요청을 받아 두고 정해 둔 응답을 돌려준다. 정해 둔 응답이 없으면 200. */
  private static final class FakeTransport implements ApnsTransport {

    private final List<Request> requests = new ArrayList<>();
    private final Deque<Object> outcomes = new ArrayDeque<>();

    void respond(final ApnsResponse response) {
      outcomes.add(response);
    }

    void fail(final Exception exception) {
      outcomes.add(exception);
    }

    Request only() {
      assertThat(requests).hasSize(1);
      return requests.get(0);
    }

    @Override
    public ApnsResponse post(final URI uri, final Map<String, String> headers, final String body)
        throws IOException, InterruptedException {
      requests.add(new Request(uri, Map.copyOf(headers), body));
      Object outcome = outcomes.isEmpty() ? new ApnsResponse(200, "") : outcomes.poll();
      if (outcome instanceof IOException e) {
        throw e;
      }
      if (outcome instanceof InterruptedException e) {
        throw e;
      }
      if (outcome instanceof RuntimeException e) {
        throw e;
      }
      return (ApnsResponse) outcome;
    }
  }
}
