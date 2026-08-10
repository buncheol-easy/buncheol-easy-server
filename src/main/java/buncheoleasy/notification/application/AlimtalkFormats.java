package buncheoleasy.notification.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 알림톡 변수 표시 포맷. 금액은 천단위 콤마, 시각은 KST 로 표시한다. */
public final class AlimtalkFormats {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private static final DateTimeFormatter DUE_AT_FORMAT =
      DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);

  private AlimtalkFormats() {}

  public static String amount(final long won) {
    return String.format("%,d", won);
  }

  /** 입금 기한 표시 (KST, 예: "8월 12일(수) 15:00"). */
  public static String dueAt(final Instant instant) {
    return DUE_AT_FORMAT.format(instant.atZone(KST));
  }
}
