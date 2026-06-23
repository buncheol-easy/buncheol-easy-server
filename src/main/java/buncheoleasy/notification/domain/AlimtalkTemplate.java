package buncheoleasy.notification.domain;

import java.util.Map;

/**
 * 카카오 알림톡 템플릿. {@code body} 는 알리고에 등록·승인된 본문과 개행까지 동일해야 하며, 발송 전 모든 {@code #{변수}} 가 치환돼야 한다. 운송장은
 * 택배사 문구가 본문에 고정돼 있어 CU/GS25 로 분리한다.
 */
public enum AlimtalkTemplate {
  PAYMENT_CONFIRMED(
      "입금 확인 안내",
      """
      #{닉네임}님, 입금이 확인되었어요!

      개최자가 입금을 확인해 참여가 확정되었어요.
      분철 진행 인원이 모이면 상품 준비가 시작돼요.

      ▪ 분철명
      #{분철명}
      ▪ 참여 멤버 : #{멤버명}
      ▪ 입금 금액 : #{입금금액}원\
      """,
      AlimtalkButton.webLink("참여 내역 보러가기", BuncheolUrls.MY_PARTICIPATIONS)),

  PAYMENT_EXPIRED(
      "참여 자동 취소 안내",
      """
      #{닉네임}님, 입금 기한이 지나 참여가 자동 취소되었어요.

      입금하셨다면 입력해주신 환불 계좌로 환불해 드려요.
      아직 입금 전이라면 따로 처리하실 내용은 없어요.

      ▪ 분철명
      #{분철명}
      ▪ 참여 멤버 : #{멤버명}\
      """,
      AlimtalkButton.webLink("참여 내역 보러가기", BuncheolUrls.MY_PARTICIPATIONS)),

  BUNCHEOL_CANCELLED(
      "분철 취소 안내",
      """
      #{닉네임}님, 참여하신 분철이 아래 사유로 취소되었어요.

      취소 사유: #{취소사유}

      입금하신 금액은 입력해주신 환불 계좌로 환불돼요.
      아직 입금 전이라면 따로 처리하실 내용은 없어요.

      ▪ 분철명
      #{분철명}
      ▪ 참여 멤버 : #{멤버명}\
      """,
      AlimtalkButton.webLink("참여 내역 보러가기", BuncheolUrls.MY_PARTICIPATIONS)),

  BUNCHEOL_CONFIRMED(
      "분철 진행 확정 안내",
      """
      #{닉네임}님, 참여하신 분철의 진행이 확정되었어요! 🎉

      최소 진행 인원이 모여 분철이 진행돼요.
      이제 상품 준비와 발송을 기다려주세요.

      ▪ 분철명
      #{분철명}
      ▪ 참여 멤버 : #{멤버명}\
      """,
      AlimtalkButton.webLink("참여 내역 보러가기", BuncheolUrls.MY_PARTICIPATIONS)),

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
      AlimtalkButton.deliveryTracking("배송조회"));

  // Aligo 알림톡 제목(subject_1, 필수 파라미터)이자 in-app 수신함 알림 제목. 카카오 기본형이라 본문 위 강조 타이틀(emtitle_1)은 쓰지 않는다.
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
