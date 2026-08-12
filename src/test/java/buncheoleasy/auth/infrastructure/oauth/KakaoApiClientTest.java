package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.auth.infrastructure.oauth.KakaoApiClient.KakaoUserInfo;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 HTTP + Jackson 매핑 경로 검증. {@code @JsonProperty} 오타나 카카오 응답 스펙 변화가 나면 값이 조용히 null 이 되어 기능이 무력화되는데,
 * Mockito 단위 테스트로는 잡을 수 없어 JDK 내장 HttpServer 로 카카오 응답 샘플을 서빙해 검증한다.
 */
@DisplayName("KakaoApiClient 응답 매핑 테스트")
class KakaoApiClientTest {

  private HttpServer server;
  private KakaoApiClient kakaoApiClient;
  private volatile String userMeResponseBody;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v2/user/me", exchange -> respondJson(exchange, userMeResponseBody));
    server.start();
    kakaoApiClient =
        new KakaoApiClient(
            "http://localhost:" + server.getAddress().getPort(),
            Duration.ofSeconds(3),
            Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void respondJson(final com.sun.net.httpserver.HttpExchange exchange, final String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  @Test
  void 이름_전화번호_연령대와_동의_상태를_파싱하고_전화번호는_정규화한다() {
    userMeResponseBody =
        """
        {"id":1,"kakao_account":{"name":"김실명","phone_number":"+82 10-1234-5678",
        "age_range":"20~29","age_range_needs_agreement":false,"email":"t@example.com"}}
        """;

    KakaoUserInfo result = kakaoApiClient.getUserInfo("token");

    assertThat(result.name()).isEqualTo("김실명");
    assertThat(result.phoneNumber()).isEqualTo("01012345678");
    assertThat(result.ageRange()).isEqualTo("20~29");
    assertThat(result.ageRangeNeedsAgreement()).isFalse();
  }

  @Test
  void 연령대_미동의면_needs_agreement_true_와_값_부재로_내려온다() {
    userMeResponseBody =
        """
        {"id":1,"kakao_account":{"name":"김실명","phone_number":"+82 10-1234-5678",
        "age_range_needs_agreement":true}}
        """;

    KakaoUserInfo result = kakaoApiClient.getUserInfo("token");

    assertThat(result.ageRange()).isNull();
    assertThat(result.ageRangeNeedsAgreement()).isTrue();
  }

  @Test
  void 동의항목_권한이_없으면_필드_부재로_전부_null_이다() {
    userMeResponseBody = """
        {"id":1,"kakao_account":{"email":"t@example.com"}}
        """;

    KakaoUserInfo result = kakaoApiClient.getUserInfo("token");

    assertThat(result.name()).isNull();
    assertThat(result.phoneNumber()).isNull();
    assertThat(result.ageRange()).isNull();
    assertThat(result.ageRangeNeedsAgreement()).isNull();
  }

  @Test
  void kakao_account_가_없으면_전부_null_인_결과를_반환한다() {
    userMeResponseBody = "{\"id\":1}";

    KakaoUserInfo result = kakaoApiClient.getUserInfo("token");

    assertThat(result.name()).isNull();
    assertThat(result.ageRange()).isNull();
    assertThat(result.ageRangeNeedsAgreement()).isNull();
  }
}
