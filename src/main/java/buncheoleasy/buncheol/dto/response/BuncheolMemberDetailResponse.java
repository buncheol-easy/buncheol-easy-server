package buncheoleasy.buncheol.dto.response;

/**
 * 분철 상세 화면의 멤버별 카드.
 *
 * @param price 호스트가 설정한 해당 멤버의 고정 금액 (100원 단위)
 * @param available 현재 참여 가능 여부 (활성 참여가 없으면 true=판매중, 있으면 false=마감)
 */
public record BuncheolMemberDetailResponse(
    Long buncheolMemberId,
    Long memberId,
    String memberName,
    String memberImage,
    long price,
    boolean available) {}
