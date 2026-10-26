package buncheoleasy.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 앱 로그인 일회용 코드를 시작한 앱만 쓸 수 있게 묶는 PKCE(RFC 7636, S256). 앱은 무작위 verifier 를 숨겨 두고 {@code challenge =
 * BASE64URL(SHA256(verifier))} 만 보낸 뒤, 코드를 바꿀 때 verifier 를 낸다 — 앱 주소를 가로챈 다른 앱은 코드를 받아도 verifier 가 없다.
 */
public final class Pkce {

  /** S256 challenge 는 SHA-256(32바이트)을 패딩 없이 base64url 로 쓴 43자다. */
  private static final Pattern CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private Pkce() {}

  public static boolean isValidChallenge(final String challenge) {
    return challenge != null && CHALLENGE_PATTERN.matcher(challenge).matches();
  }

  /** verifier 로 만든 challenge 가 저장된 challenge 와 같은지 본다. 비교는 상수 시간이다. */
  public static boolean matches(final String verifier, final String challenge) {
    if (verifier == null || challenge == null) {
      return false;
    }
    byte[] computed =
        BASE64_URL.encodeToString(Sha256.hash(verifier)).getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(computed, challenge.getBytes(StandardCharsets.US_ASCII));
  }
}
