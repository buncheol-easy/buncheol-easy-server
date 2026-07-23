package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배송비 환급 신청에 제출하는 X(트위터) 후기 트윗 URL. 공유 시 붙는 쿼리스트링({@code ?s=20})·프래그먼트·status id 뒤 추가
 * 경로({@code /photo/1} 등)를 제거한 퍼머링크로 정규화해 저장한다 — 같은 트윗의 중복 신청을 {@code payback_tweet_url} 유니크
 * 인덱스로 잡으려면 표기가 유일해야 한다.
 */
public record PaybackTweetUrl(String value) {

  // 핸들은 X 정책상 영숫자+언더스코어 15자 이내, status id 는 숫자. twitter.com 구 도메인도 허용한다.
  private static final Pattern TWEET_URL_PATTERN =
      Pattern.compile("^https://(x|twitter)\\.com/[A-Za-z0-9_]+/status/\\d+$");

  // 정규화용 퍼머링크 접두 매칭 (끝 미강제). 매칭 구간까지만 잘라 TWEET_URL_PATTERN 을 만족시킨다.
  private static final Pattern TWEET_URL_PREFIX_PATTERN =
      Pattern.compile("^https://(x|twitter)\\.com/[A-Za-z0-9_]+/status/\\d+");

  public PaybackTweetUrl {
    if (value == null || !TWEET_URL_PATTERN.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
  }

  /** 원본 입력에서 퍼머링크 접두만 남기도록 정규화하고 형식을 검증한다. */
  public static PaybackTweetUrl parse(final String raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
    String stripped = raw.strip();
    Matcher prefix = TWEET_URL_PREFIX_PATTERN.matcher(stripped);
    if (!prefix.lookingAt() || !isPermalinkBoundary(stripped, prefix.end())) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
    return new PaybackTweetUrl(prefix.group());
  }

  // status id 바로 뒤는 문자열 끝 또는 경로/쿼리/프래그먼트 구분자여야 한다 — "status/123abc" 를
  // 트윗 123 으로 잘라 수용하는 오탐을 막는다.
  private static boolean isPermalinkBoundary(final String value, final int end) {
    if (end >= value.length()) {
      return true;
    }
    char next = value.charAt(end);
    return next == '/' || next == '?' || next == '#';
  }
}
