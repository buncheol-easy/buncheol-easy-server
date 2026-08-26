package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.SlotAccessType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
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
  public boolean changeAccessTypeIfUnoccupied(
      final Long buncheolMemberId, final Long buncheolId, final SlotAccessType accessType) {
    return jpaBuncheolMemberRepository.changeAccessTypeIfUnoccupied(
            buncheolMemberId, buncheolId, accessType, ParticipationStatus.active())
        > 0;
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
  public List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(Long buncheolId) {
    return jpaBuncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);
  }

  @Override
  public List<BuncheolMember> findAllByBuncheolIds(List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolMemberRepository.findAllByBuncheolIds(buncheolIds);
  }

  @Override
  public List<Long> findAllFreeSlotBuncheolIds(List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolMemberRepository.findAllFreeSlotBuncheolIds(buncheolIds);
  }

  @Override
  public void deleteById(Long id) {
    jpaBuncheolMemberRepository.deleteById(id);
  }
}
