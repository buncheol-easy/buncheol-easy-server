package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.alias.GroupAlias;
import buncheoleasy.group.domain.alias.GroupAliasRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaGroupAliasRepositoryAdapter implements GroupAliasRepository {

  private final JpaGroupAliasRepository jpaGroupAliasRepository;

  @Override
  public List<GroupAlias> findAllByGroupIds(List<Long> groupIds) {
    if (groupIds.isEmpty()) {
      return List.of();
    }
    return jpaGroupAliasRepository.findAllByGroupIdInOrderByGroupIdAscIdAsc(groupIds);
  }
}
