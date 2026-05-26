package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 로그인 유저가 이 분철에서 가진 활성 참여 요약.
 *
 * @param participatedMemberCount 이 분철 내 내가 활성 참여 중인 distinct 멤버 슬롯 수
 * @param bids 내 활성 입찰 목록 (멤버별 1건)
 */
public record MyParticipationSummaryResponse(
    int participatedMemberCount, List<MyBidResponse> bids) {}
