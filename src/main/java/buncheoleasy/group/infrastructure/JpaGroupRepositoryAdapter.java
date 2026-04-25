package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaGroupRepositoryAdapter implements GroupRepository {

  private final JpaGroupRepository jpaGroupRepository;

  @Override
  public boolean existsById(Long id) {
    return jpaGroupRepository.existsById(id);
  }

  @Override
  public Optional<Group> findById(Long id) {
    return jpaGroupRepository.findById(id);
  }

  @Override
  public List<Group> findByKeyword(String keyword) {
    return jpaGroupRepository.findByKeyword(keyword);
  }
}
