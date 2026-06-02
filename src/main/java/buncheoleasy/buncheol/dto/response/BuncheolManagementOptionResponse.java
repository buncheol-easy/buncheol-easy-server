package buncheoleasy.buncheol.dto.response;

/**
 * 개최자 분철 관리 화면의 옵션(멤버 슬롯) 카드.
 *
 * @param memberName 그룹 멤버명. 멤버가 삭제·이동돼 조회되지 않으면 null
 * @param memberImage 그룹 멤버 이미지 URL. 멤버가 삭제·이동돼 조회되지 않으면 null
 * @param participationCount 옵션별 참여 수 (ACTIVE_BID + AWAITING_PAYMENT + CONFIRMED). 한 유저가 한 슬롯에 활성
 *     참여는 최대 1건이므로 사실상 참여자 수와 동일하다
 * @param currentHighestBid 옵션별 최고 제시 금액 (상태 무관). 참여 없으면 null. 마감 후에는 낙찰가({@link WinnerDeliveryResponse}
 *     의 입찰가)보다 높은 미낙찰 활성 입찰가가 남아 있을 수 있어 winner 의 금액과 다를 수 있다
 * @param winner 낙찰 확정(CONFIRMED)자의 배송/운송장 정보. 낙찰 전 또는 결제 미완료 시 null. 슬롯당 최대 1명이라 null 여부로 낙찰 여부를 판단한다
 */
public record BuncheolManagementOptionResponse(
    Long buncheolMemberId,
    Long memberId,
    String memberName,
    String memberImage,
    int participationCount,
    Long currentHighestBid,
    WinnerDeliveryResponse winner) {}
