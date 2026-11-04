package buncheoleasy.push.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * APNs(애플 푸시) 발송 설정. 네 값이 모두 있어야 켜진다 — 하나라도 비면 발송기는 아무것도 보내지 않는다. 키 발급 전에 배포해도 안전하게 하려는 것이다.
 *
 * @param teamId 애플 개발자 팀 ID (제공자 토큰의 {@code iss})
 * @param keyId APNs 인증 키(.p8) ID (제공자 토큰 헤더의 {@code kid})
 * @param privateKey .p8 파일 본문(PEM, PKCS#8). env 한 줄에 넣을 수 있게 {@code \n} 글자도 줄바꿈으로 읽는다
 * @param topic 앱 번들 ID ({@code apns-topic} 헤더)
 * @param connectTimeout 애플 서버 연결 대기
 * @param requestTimeout 요청 하나의 응답 대기
 */
@Validated
@ConfigurationProperties(prefix = "app.apns")
public record ApnsProperties(
    String teamId,
    String keyId,
    String privateKey,
    String topic,
    @NotNull Duration connectTimeout,
    @NotNull Duration requestTimeout) {

  public ApnsProperties {
    if (connectTimeout != null && !connectTimeout.isPositive()
        || requestTimeout != null && !requestTimeout.isPositive()) {
      throw new IllegalArgumentException("app.apns 타임아웃은 양수여야 한다");
    }
  }

  /** 네 값이 모두 채워졌는지. 키 형식까지는 보지 않는다({@link ApnsClient#isEnabled}). */
  public boolean configured() {
    return missingEnvKeys().isEmpty();
  }

  /** 비어 있는 환경변수 이름. 값은 돌려주지 않는다. */
  public List<String> missingEnvKeys() {
    List<String> missing = new ArrayList<>();
    if (isBlank(teamId)) {
      missing.add("APNS_TEAM_ID");
    }
    if (isBlank(keyId)) {
      missing.add("APNS_KEY_ID");
    }
    if (isBlank(privateKey)) {
      missing.add("APNS_PRIVATE_KEY");
    }
    if (isBlank(topic)) {
      missing.add("APNS_TOPIC");
    }
    return missing;
  }

  /** 키는 값 대신 있는지만 보인다 — 설정 객체가 로그·오류 메시지에 찍혀도 새지 않게. */
  @Override
  public String toString() {
    return "ApnsProperties[teamId=%s, keyId=%s, privateKey=%s, topic=%s, connectTimeout=%s, requestTimeout=%s]"
        .formatted(
            teamId,
            keyId,
            isBlank(privateKey) ? "(없음)" : "(설정됨)",
            topic,
            connectTimeout,
            requestTimeout);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
