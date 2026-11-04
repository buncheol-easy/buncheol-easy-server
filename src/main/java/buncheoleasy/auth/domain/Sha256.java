package buncheoleasy.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 앱 로그인 검증값(PKCE verifier·애플 nonce)을 SHA-256 으로 줄인다. */
public final class Sha256 {

  private Sha256() {}

  public static byte[] hash(final String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      // 모든 자바 구현이 SHA-256 을 제공해야 한다(MessageDigest 명세) — 여기 오면 런타임이 망가진 것이다.
      throw new IllegalStateException(exception);
    }
  }
}
