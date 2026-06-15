package buncheoleasy.notification.domain;

/** 알림톡 버튼이 가리키는 서비스 URL. {@code #{분철아이디}} 는 발송 시 치환된다. */
final class BuncheolUrls {

  static final String BASE = "https://buncheoleasy.com";
  static final String MY_BIDS = BASE + "/profile/bids";
  static final String HOME = BASE;
  static final String BUNCHEOL_MANAGE = BASE + "/products/#{분철아이디}/manage";

  private BuncheolUrls() {}
}
