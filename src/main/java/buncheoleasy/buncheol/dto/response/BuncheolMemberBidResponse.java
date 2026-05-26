package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 분철 상세 화면의 멤버별 입찰 카드.
 *
 * @param topBidAmounts 실시간 활성 입찰 금액 DESC 상위 N개 (최대 3개)
 * @param activeParticipantCount 해당 멤버의 현재 활성 참여자 수
 */
public record BuncheolMemberBidResponse(
    Long buncheolMemberId,
    Long memberId,
    String memberName,
    String memberImage,
    List<Long> topBidAmounts,
    int activeParticipantCount) {}
