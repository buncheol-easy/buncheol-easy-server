package buncheoleasy.aidraft.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Locale;

/**
 * 초안 재료 — 판매 페이지 주소와 붙여 넣은 글 중 정확히 하나. 엔진도 같은 검사를 하지만 여기서 먼저 막아 잠금·하루 횟수를 쓰지 않게 한다. 위험 주소(내부망
 * 등) 판정은 엔진의 가드가 한다 — 이름 풀이까지 해야 알 수 있어서다.
 */
public record AiDraftSource(AiDraftSourceType type, String url, String rawText) {

  /** 브라우저 주소창이 실제로 다루는 길이 수준. 판매 페이지 주소가 이보다 길 일은 없다. */
  public static final int URL_MAX_LENGTH = 2000;

  /**
   * 엔진은 붙여 넣은 글의 앞 2,000자를 본문 재료로 쓰고 가격·날짜는 글 전체에서 찾는다. 상품 페이지 설명을 통째로 붙여도 들어가도록 넉넉히 잡되 요청 크기는
   * 묶는다.
   */
  public static final int RAW_TEXT_MAX_LENGTH = 5000;

  private static final int HOST_MAX_LENGTH = 255;

  public AiDraftSource {
    boolean valid =
        switch (type) {
          case URL -> rawText == null && isValidUrl(url);
          case TEXT -> url == null && rawText != null && !rawText.isBlank()
              && rawText.length() <= RAW_TEXT_MAX_LENGTH;
        };
    if (!valid) {
      throw new BusinessException(ErrorCode.AI_DRAFT_INVALID_INPUT);
    }
  }

  /** 요청의 두 칸에서 재료를 고른다. 앞뒤 공백은 지운다. 둘 다 있거나 둘 다 없으면 거절한다. */
  public static AiDraftSource of(final String url, final String rawText) {
    String trimmedUrl = trimToNull(url);
    String trimmedText = trimToNull(rawText);
    if ((trimmedUrl == null) == (trimmedText == null)) {
      throw new BusinessException(ErrorCode.AI_DRAFT_INVALID_INPUT);
    }
    return trimmedUrl != null
        ? new AiDraftSource(AiDraftSourceType.URL, trimmedUrl, null)
        : new AiDraftSource(AiDraftSourceType.TEXT, null, trimmedText);
  }

  /** 기록용 호스트(소문자). 붙여 넣은 글이면 null. */
  public String host() {
    if (type != AiDraftSourceType.URL) {
      return null;
    }
    return AiDraftText.fit(extractHost(url).toLowerCase(Locale.ROOT), HOST_MAX_LENGTH);
  }

  /** 기록용 붙여 넣은 글 길이. 글 자체는 저장하지 않는다. */
  public Integer rawTextLength() {
    return rawText == null ? null : rawText.length();
  }

  /**
   * 엔진과 같은 기준(http/https 로 시작)에 호스트가 있고 공백이 없는 주소. URI 로 엄격하게 파싱하지 않는 것은 한글이 그대로 든
   * 주소도 엔진은 받기 때문이다.
   */
  private static boolean isValidUrl(final String url) {
    if (url == null || url.length() > URL_MAX_LENGTH || url.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    String lower = url.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return false;
    }
    return !extractHost(url).isEmpty();
  }

  /** {@code scheme://[userinfo@]host[:port]/...} 에서 host 만. IPv6 대괄호 표기는 대괄호째 둔다. */
  private static String extractHost(final String url) {
    String rest = url.substring(url.indexOf("://") + 3);
    int end = rest.length();
    for (char delimiter : new char[] {'/', '?', '#'}) {
      int index = rest.indexOf(delimiter);
      if (index >= 0) {
        end = Math.min(end, index);
      }
    }
    String authority = rest.substring(0, end);
    String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
    if (hostAndPort.startsWith("[")) {
      int close = hostAndPort.indexOf(']');
      return close < 0 ? "" : hostAndPort.substring(0, close + 1);
    }
    int colon = hostAndPort.indexOf(':');
    return colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
  }

  private static String trimToNull(final String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
