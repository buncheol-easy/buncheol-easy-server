package buncheoleasy.buncheol.dto.response;

/**
 * 분철 상세 화면의 멤버별 카드.
 *
 * @param price 호스트가 설정한 해당 멤버의 고정 금액 (100원 단위)
 * @param saleStatus 판매 상태 (AVAILABLE=공석 / AWAITING_PAYMENT=선점 후 입금 확인 중 / SOLD=판매 완료)
 */
public record BuncheolMemberDetailResponse(
    Long buncheolMemberId,
    Long memberId,
    String memberName,
    String memberImage,
    long price,
    BuncheolMemberSaleStatus saleStatus) {}
