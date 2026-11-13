package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.popularity.GroupMemberPopularity;
import buncheoleasy.group.domain.popularity.GroupMemberPopularityRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaGroupMemberPopularityRepositoryAdapter implements GroupMemberPopularityRepository {

  private final JpaGroupMemberPopularityRepository jpaGroupMemberPopularityRepository;

  @Override
  public List<GroupMemberPopularity> findAllByGroupId(final Long groupId) {
    return jpaGroupMemberPopularityRepository.findAllByGroupId(groupId);
  }
}
