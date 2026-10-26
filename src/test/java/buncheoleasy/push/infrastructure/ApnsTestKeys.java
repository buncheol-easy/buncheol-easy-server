package buncheoleasy.push.infrastructure;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

/** APNs 테스트용 키·시계. 키는 테스트마다 새로 만든다 — 실제 .p8 키를 저장소에 두지 않는다. */
final class ApnsTestKeys {

  private ApnsTestKeys() {}

  /** 애플 APNs 키와 같은 종류(P-256 EC). */
  static KeyPair newKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** .p8 파일과 같은 모양(PEM, PKCS#8)으로 쓴다. */
  static String pem(final PrivateKey key) {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
  }

  /** 테스트가 시간을 앞으로 돌릴 수 있는 시계. */
  static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(final Instant now) {
      this.now = now;
    }

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
