package buncheoleasy.notification.domain;

import java.util.Map;

/**
 * 카카오 알림톡 템플릿. {@code body} 는 알리고에 등록·승인된 본문과 개행까지 동일해야 하며, 발송 전 모든 {@code #{변수}} 가 치환돼야 한다. 운송장은
 * 택배사 문구가 본문에 고정돼 있어 CU/GS25 로 분리한다.
 */
public enum AlimtalkTemplate {
  PARTICIPATION_WON(
      "분철 낙찰 안내",
      """
      #{닉네임}님, 낙찰을 축하해요! 🎉

      참여하신 분철에 낙찰되었어요.
      기한 내에 입금 후 '입금 완료'를 눌러주세요.

      ▪ 분철명
      #{분철명}
      ▪ 낙찰 멤버 : #{멤버명}
      ▪ 입금 금액 : #{입금금액}원
      ▪ 입금 기한 : #{입금기한}

      기한이 지나면 차순위 참여자에게 넘어가요.\
      """,
      AlimtalkButton.webLink("입금 정보 확인하기", BuncheolUrls.MY_BIDS)),

  BUNCHEOL_CANCELLED(
      "분철 취소 안내",
      """
      #{닉네임}님, 참여하신 분철이 개최자 사정으로 취소되었어요.
      결제 전 단계라 따로 처리하실 내용은 없어요.

      ▪ 분철명
      #{분철명}
      ▪ 참여 멤버 : #{멤버명}

      다른 분철을 둘러보시는 건 어떠신가요?\
      """,
      AlimtalkButton.webLink("확인하기", BuncheolUrls.HOME)),

  TRACKING_CU(
      "운송장 등록 안내",
      """
      #{닉네임}님, 상품이 발송되었어요! 📦

      ▪ 분철명
      #{분철명}
      ▪ 상품(멤버) : #{멤버명}
      ▪ 택배사 : CU 편의점 택배
      ▪ 운송장 번호 : #{운송장번호}\
      """,
      AlimtalkButton.deliveryTracking("배송조회")),

  TRACKING_GS25(
      "운송장 등록 안내",
      """
      #{닉네임}님, 상품이 발송되었어요! 📦

      ▪ 분철명
      #{분철명}
      ▪ 상품(멤버) : #{멤버명}
      ▪ 택배사 : GS25 편의점 택배
      ▪ 운송장 번호 : #{운송장번호}\
      """,
      AlimtalkButton.deliveryTracking("배송조회")),

  PAYMENT_CONFIRMED(
      "입금 확인 안내",
      """
      #{닉네임}님, 입금이 확인되었어요!

      개최자가 입금을 확인해 참여가 확정되었어요.
      이제 상품 준비와 발송을 기다려주세요.

      ▪ 분철명
      #{분철명}
      ▪ 낙찰 멤버 : #{멤버명}
      ▪ 입금 금액 : #{입금금액}원\
      """,
      AlimtalkButton.webLink("참여 내역 보기", BuncheolUrls.MY_BIDS)),

  PAYMENT_DUE_IMMINENT(
      "입금 기한 임박 안내",
      """
      #{닉네임}님, 입금 기한이 곧 마감돼요! ⏰

      아직 입금이 확인되지 않았어요.
      기한이 지나면 낙찰이 취소되고 차순위 참여자에게 넘어가요.

      ▪ 분철명
      #{분철명}
      ▪ 낙찰 멤버 : #{멤버명}
      ▪ 입금 금액 : #{입금금액}원
      ▪ 입금 기한 : #{입금기한}\
      """,
      AlimtalkButton.webLink("입금 정보 확인하기", BuncheolUrls.MY_BIDS)),

  PAYMENT_REPORTED(
      "입금 확인 요청 안내",
      """
      #{닉네임}님, 입금 확인 요청이 도착했어요.

      참여자가 입금 완료 후 확인을 요청했어요.
      입금 내역을 확인하고 처리해주세요.

      ▪ 분철명
      #{분철명}
      ▪ 참여자 : #{참여자닉네임}
      ▪ 낙찰 멤버 : #{멤버명}
      ▪ 입금 금액 : #{입금금액}원
      ▪ 요청 시각 : #{신고시각}\
      """,
      AlimtalkButton.webLink("입금 확인하러 가기", BuncheolUrls.BUNCHEOL_MANAGE));

  private final String subject;
  private final String body;
  private final AlimtalkButton button;

  AlimtalkTemplate(final String subject, final String body, final AlimtalkButton button) {
    this.subject = subject;
    this.body = body;
    this.button = button;
  }

  public String subject() {
    return subject;
  }

  public AlimtalkButton button() {
    return button;
  }

  /** {@code #{변수}} 를 주어진 값으로 치환한다. 호출자가 모든 변수를 채워야 하며, 미치환 토큰이 남으면 발송 단계에서 거른다. */
  public String render(final Map<String, String> variables) {
    return AlimtalkPlaceholders.replace(body, variables);
  }
}
