package buncheoleasy.group.domain.member;

import java.util.List;

public interface GroupMemberRepository {

  List<GroupMember> findAllByGroupIdAndIds(Long groupId, List<Long> memberIds);

  List<GroupMember> findAllByGroupId(Long groupId);

  List<GroupMember> findAllByIds(List<Long> memberIds);

  /** 정규화된 검색어와 멤버명이 부분일치하는 멤버. 기존 정확일치 조회를 대체한다 ("장 원영" 으로도 "장원영" 이 걸리게). */
  List<GroupMember> findAllByNormalizedName(String normalizedName);

  /** 정규화된 검색어와 부분일치하는 멤버 id 만. 분철 검색이 멤버명까지 커버하기 위한 사전 해석용. */
  List<Long> findIdsByNormalizedName(String normalizedName);

  List<GroupMember> findAllByGroupIds(List<Long> groupIds);
}
