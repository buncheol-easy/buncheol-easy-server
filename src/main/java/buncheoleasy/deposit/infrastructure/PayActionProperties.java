package buncheoleasy.deposit.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 페이액션(입금 자동확인 SaaS) 연동 설정. 키는 시크릿이라 환경변수로만 주입하며, 비어 있으면 연동을 꺼둔다 — 로컬·테스트뿐 아니라 운영에서도 키 발급 전까지
 * 서버가 정상 기동해야 하므로 필수값으로 두지 않는다(슬랙 웹훅과 같은 방식).
 *
 * <p>{@code webhookKey} 는 발신자가 페이액션임을 검증하는 공유 비밀키다. 페이액션은 HMAC 서명을 제공하지 않아 이 값 비교가 유일한 인증 수단이다.
 */
@Validated
@ConfigurationProperties(prefix = "payaction")
public record PayActionProperties(
    String baseUrl,
    String apiKey,
    String mallId,
    String webhookKey,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {

  /** 주문 등록·매칭제외를 호출할 수 있는 환경인지. 미설정 환경에서는 호출을 건너뛴다. */
  public boolean outboundEnabled() {
    return isSet(baseUrl) && isSet(apiKey) && isSet(mallId);
  }

  /**
   * 수신 웹훅을 검증할 수 있는 환경인지. 검증 키가 없으면 웹훅을 신뢰할 수 없으므로 수신 자체를 거부한다. 상점ID 는 비밀값이 아니라 인증 강도에 기여하지 않으므로
   * 여기서 요구하지 않는다.
   */
  public boolean webhookEnabled() {
    return isSet(webhookKey);
  }

  private static boolean isSet(final String value) {
    return value != null && !value.isBlank();
  }
}
