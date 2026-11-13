package buncheoleasy.group.domain.popularity;

import java.util.List;

public interface GroupMemberPopularityRepository {

  /** 그룹 멤버 중 순위가 있는 멤버만. 순위 오름차순. */
  List<GroupMemberPopularity> findAllByGroupId(Long groupId);
}
