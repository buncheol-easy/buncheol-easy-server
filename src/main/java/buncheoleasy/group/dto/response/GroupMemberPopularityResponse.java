package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.popularity.GroupMemberPopularity;

/**
 * @param memberId 멤버 ID
 * @param rank 출처 순위표 안의 순위(1 이 가장 높다). 그룹 안 순서를 정하는 데만 쓴다
 */
public record GroupMemberPopularityResponse(Long memberId, int rank) {

  public static GroupMemberPopularityResponse from(final GroupMemberPopularity popularity) {
    return new GroupMemberPopularityResponse(
        popularity.getMemberId(), popularity.getPopularityRank());
  }
}
