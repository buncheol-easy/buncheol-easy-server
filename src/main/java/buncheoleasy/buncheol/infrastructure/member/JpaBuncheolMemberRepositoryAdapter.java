package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolMemberRepositoryAdapter implements BuncheolMemberRepository {

  private final JpaBuncheolMemberRepository jpaBuncheolMemberRepository;

  @Override
  public List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers) {
    if (buncheolMembers.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolMemberRepository.saveAll(buncheolMembers);
  }

  @Override
  public void deleteAllByBuncheolId(Long buncheolId) {
    jpaBuncheolMemberRepository.deleteAllByBuncheolId(buncheolId);
  }

  @Override
  public Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId) {
    return jpaBuncheolMemberRepository.findByIdAndBuncheolId(id, buncheolId);
  }

  @Override
  public List<BuncheolMember> findAllByBuncheolId(Long buncheolId) {
    return jpaBuncheolMemberRepository.findAllByBuncheolId(buncheolId);
  }

  @Override
  public List<BuncheolMember> findAllByBuncheolIds(List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolMemberRepository.findAllByBuncheolIds(buncheolIds);
  }

  @Override
  public void deleteById(Long id) {
    jpaBuncheolMemberRepository.deleteById(id);
  }
}
