package buncheoleasy.notification.domain;

/**
 * 운송장 등록 알림톡의 "배송조회" 버튼이 가리키는 택배사 배송조회 URL. {@code #{운송장번호}} 는 발송 시 치환된다. 카카오 등록 템플릿의 버튼 링크와 정확히 일치해야
 * 하므로(불일치 시 미발송) URL 형식을 임의로 바꾸지 않는다.
 */
final class CourierUrls {

  static final String CU = "https://www.cupost.co.kr/mobile/delivery/allResult.cupost?invoice_no=#{운송장번호}";
  static final String GS25 = "https://www.cvsnet.co.kr/invoice/tracking.do?invoice_no=#{운송장번호}";

  private CourierUrls() {}
}
