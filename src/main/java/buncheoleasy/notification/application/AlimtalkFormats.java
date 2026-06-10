package buncheoleasy.notification.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 알림톡 변수 표시 포맷. 금액은 천단위 콤마, 시각은 KST 기준("6/10(화) 15:00"). */
public final class AlimtalkFormats {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);

  private AlimtalkFormats() {}

  public static String amount(final long won) {
    return String.format("%,d", won);
  }

  public static String dateTime(final Instant instant) {
    return DATE_TIME.format(instant.atZone(KST));
  }
}
