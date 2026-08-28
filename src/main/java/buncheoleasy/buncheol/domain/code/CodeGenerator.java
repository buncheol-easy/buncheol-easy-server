package buncheoleasy.buncheol.domain.code;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 코드는 슬롯 배정 권한 그 자체라 {@link SecureRandom} 을 쓴다 (32^8 ≈ 1.1×10^12). */
@Component
public class CodeGenerator {

  private static final int LENGTH = 8;

  private final SecureRandom random = new SecureRandom();

  public CodeText generate() {
    StringBuilder builder = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      builder.append(CodeText.ALPHABET.charAt(random.nextInt(CodeText.ALPHABET.length())));
    }
    return new CodeText(builder.toString());
  }
}
