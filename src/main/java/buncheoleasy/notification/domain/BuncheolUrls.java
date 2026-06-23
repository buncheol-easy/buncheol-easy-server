package buncheoleasy.notification.domain;

/**
 * 알림톡 버튼이 가리키는 프론트 화면의 상대 경로. {@code #{baseUrl}}(프론트 origin, 환경별 주입) 은 발송 시 치환된다. 프론트 라우트는 코드로 버전
 * 관리되는 계약이라 경로 자체는 상수로 둔다(환경에 따라 바뀌는 base 만 외부 주입).
 */
final class BuncheolUrls {

  static final String MY_PARTICIPATIONS = "#{baseUrl}/profile/bids";

  private BuncheolUrls() {}
}
