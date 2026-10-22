package buncheoleasy.aidraft.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 하루 횟수를 세는 날. 한국 시간 자정에 초기화한다 — 서버·DB 는 UTC 라 UTC 날짜로 세면 오전 9시에 초기화된다.
 *
 * @param date 한국 날짜. 카운터 키에 들어간다
 * @param keyTtl 이 날 카운터 키의 수명. 다음 한국 자정까지 남은 시간 + 여유 1시간이라 늘 1시간 이상이다
 */
public record AiDraftDay(LocalDate date, Duration keyTtl) {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  /** 자정 직전에 시작한 요청이 자정 뒤에 횟수를 돌려줄 때도 그날 키가 남아 있게 하는 여유. 키에 날짜가 있어 길게 남아도 다음 날과 섞이지 않는다. */
  private static final Duration EXPIRY_MARGIN = Duration.ofHours(1);

  public AiDraftDay {
    // 카운터는 수명을 밀리초로 보낸다. 0ms 이하면 PEXPIRE 가 키를 바로 지워 하루 한도가 소리 없이 사라진다.
    if (keyTtl == null || keyTtl.toMillis() <= 0) {
      throw new IllegalArgumentException("AI 개최글 하루 키 수명은 1ms 이상이어야 한다: " + keyTtl);
    }
  }

  public static AiDraftDay of(final Instant now) {
    LocalDate date = LocalDate.ofInstant(now, KST);
    Instant nextMidnight = date.plusDays(1).atStartOfDay(KST).toInstant();
    return new AiDraftDay(date, Duration.between(now, nextMidnight).plus(EXPIRY_MARGIN));
  }
}
