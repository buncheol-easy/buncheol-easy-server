package buncheoleasy.notification.domain;

/** 알림톡 버튼 링크 타입. 알리고 {@code linkType} / {@code linkTypeName} 에 대응한다. */
public enum AlimtalkButtonType {
  WL("웹링크"),
  DS("배송조회");

  private final String displayName;

  AlimtalkButtonType(final String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
