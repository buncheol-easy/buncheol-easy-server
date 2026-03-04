package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisGroupRepository implements GroupRepository {

  private final GroupMapper groupMapper;

  @Override
  public boolean existsById(final Long id) {
    return groupMapper.existsById(id);
  }

  @Override
  public Optional<Group> findById(final Long id) {
    return groupMapper.findById(id);
  }

  @Override
  public List<Group> findByKeyword(final String keyword) {
    return groupMapper.selectByKeyword(keyword);
  }
}
