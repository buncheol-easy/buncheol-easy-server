package buncheoleasy.buncheol.dto.response;

/**
 * 로그인 유저가 이 분철의 특정 멤버 슬롯에 가진 활성 입찰 1건.
 *
 * @param participationId 입찰 철회에 사용
 * @param rank 해당 멤버 내 입찰 금액 순위 (1-base)
 */
public record MyBidResponse(
    Long participationId, Long buncheolMemberId, long bidAmount, int rank) {}
