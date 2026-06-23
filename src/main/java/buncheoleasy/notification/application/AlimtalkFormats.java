package buncheoleasy.notification.application;

/** 알림톡 변수 표시 포맷. 금액은 천단위 콤마. */
public final class AlimtalkFormats {

  private AlimtalkFormats() {}

  public static String amount(final long won) {
    return String.format("%,d", won);
  }
}
