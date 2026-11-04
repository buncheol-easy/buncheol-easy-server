package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.user.domain.PhoneNumber;

/**
 * 카카오가 내려주는 전화번호("+82 10-1234-5678" 형태)를 서비스 표준 형식("01012345678")으로 정규화한다. {@link PhoneNumber} 규칙에
 * 맞지 않으면 null 을 반환해 가입은 진행시키고 번호는 화면에서 보완받는다.
 */
public final class KakaoPhoneNumberNormalizer {

  private KakaoPhoneNumberNormalizer() {}

  public static String normalize(final String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }

    String digits = rawValue.replaceAll("\\D", "");
    if (digits.startsWith("82")) {
      digits = "0" + digits.substring(2);
    }
    return PhoneNumber.isValid(digits) ? digits : null;
  }
}
