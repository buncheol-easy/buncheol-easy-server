package buncheoleasy.aidraft.domain;

/** 엔진이 준 글을 개최 폼 칸 길이에 맞춘다. */
public final class AiDraftText {

  private AiDraftText() {}

  /**
   * {@code maxLength} 를 넘으면 자른다. 길이는 Java 문자(UTF-16) 수로 센다 — 개최 요청의 {@code @Size} 와 분철 도메인 검증이 세는 단위다. 엔진(파이썬)은
   * 코드 포인트로 세서, 이모지가 섞이면 엔진 기준으로 상한 안인 글이 여기서는 넘는다. 이모지(서로게이트 쌍)를 반으로 자르지 않는다.
   */
  public static String fit(final String value, final int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    int end = maxLength;
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(0, end);
  }
}
