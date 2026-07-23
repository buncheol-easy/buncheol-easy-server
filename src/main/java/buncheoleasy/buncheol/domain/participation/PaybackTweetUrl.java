package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

/**
 * 배송비 환급 신청에 제출하는 X(트위터) 후기 트윗 URL. 공유 시 붙는 쿼리스트링({@code ?s=20} 등)·프래그먼트를 제거한 퍼머링크로 정규화해
 * 저장한다 — 같은 트윗의 중복 신청을 {@code payback_tweet_url} 유니크 인덱스로 잡으려면 표기가 유일해야 한다.
 */
public record PaybackTweetUrl(String value) {

  // 핸들은 X 정책상 영숫자+언더스코어 15자 이내, status id 는 숫자. twitter.com 구 도메인도 허용한다.
  private static final Pattern TWEET_URL_PATTERN =
      Pattern.compile("^https://(x|twitter)\\.com/[A-Za-z0-9_]+/status/\\d+$");

  public PaybackTweetUrl {
    if (value == null || !TWEET_URL_PATTERN.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
  }

  /** 원본 입력에서 쿼리스트링·프래그먼트를 제거해 정규화하고 형식을 검증한다. */
  public static PaybackTweetUrl parse(final String raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
    String normalized = raw.strip();
    int cutAt = indexOfFirst(normalized, '?', '#');
    if (cutAt >= 0) {
      normalized = normalized.substring(0, cutAt);
    }
    return new PaybackTweetUrl(normalized);
  }

  private static int indexOfFirst(final String value, final char first, final char second) {
    int firstIndex = value.indexOf(first);
    int secondIndex = value.indexOf(second);
    if (firstIndex < 0) {
      return secondIndex;
    }
    return secondIndex < 0 ? firstIndex : Math.min(firstIndex, secondIndex);
  }
}
