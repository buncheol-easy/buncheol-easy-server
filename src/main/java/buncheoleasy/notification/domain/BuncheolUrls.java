package buncheoleasy.notification.domain;

/**
 * 알림톡 버튼이 가리키는 프론트 화면의 상대 경로. {@code #{baseUrl}}(프론트 origin, 환경별 주입) 은 발송 시 치환된다. 프론트 라우트는 코드로 버전
 * 관리되는 계약이라 경로 자체는 상수로 둔다(환경에 따라 바뀌는 base 만 외부 주입).
 */
final class BuncheolUrls {

  static final String MY_PARTICIPATIONS = "#{baseUrl}/profile/participations";

  // 개최자 분철 관리 화면. 분철별 경로라 #{분철ID} 도 발송 시 변수로 치환된다(알리고 등록본 버튼 링크에 변수 포함 필요).
  static final String BUNCHEOL_MANAGE = "#{baseUrl}/products/#{분철ID}/manage";

  // 분철 개최 화면. 취소된 분철은 다시 열 수 없어 자동 취소 안내 뒤 개최자가 할 수 있는 일은 새로 개최하는 것뿐이다.
  static final String UPLOAD = "#{baseUrl}/upload";

  private BuncheolUrls() {}
}
