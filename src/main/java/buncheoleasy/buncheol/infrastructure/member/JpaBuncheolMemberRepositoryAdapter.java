package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolMemberRepositoryAdapter implements BuncheolMemberRepository {

  private static final String UNIQUE_BUNCHEOL_MEMBER_INDEX = "uq_buncheol_members_buncheol_member";

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
      final Long buncheolMemberId, final Long buncheolId, final BuncheolMemberAccessType accessType) {
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

  @Override
  public BuncheolMember save(final BuncheolMember buncheolMember) {
    try {
      // 커밋까지 미루면 유니크 위반이 어댑터 밖에서 raw 예외로 터져 500 이 된다 — 여기서 flush 해 변환한다.
      return jpaBuncheolMemberRepository.saveAndFlush(buncheolMember);
    } catch (DataIntegrityViolationException ex) {
      // 다른 제약(멤버 FK 등) 위반을 중복으로 오분류하지 않게 인덱스명으로 좁힌다. MySQL·H2 모두 메시지에
      // 인덱스명이 실리고 H2 는 대문자일 수 있다(JpaParticipationRepositoryAdapter#isViolationOf 와 같은 방식).
      if (String.valueOf(ex.getMessage())
          .toLowerCase()
          .contains(UNIQUE_BUNCHEOL_MEMBER_INDEX)) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
      }
      throw ex;
    }
  }

  @Override
  public boolean existsByBuncheolIdAndMemberId(final Long buncheolId, final Long memberId) {
    return jpaBuncheolMemberRepository.existsByBuncheolIdAndMemberId(buncheolId, memberId);
  }

  @Override
  public List<BuncheolMember> findAllByBuncheolIdForUpdate(final Long buncheolId) {
    return jpaBuncheolMemberRepository.findAllByBuncheolIdForUpdate(buncheolId);
  }

  @Override
  public boolean deleteIfUnreferenced(final Long buncheolMemberId, final Long buncheolId) {
    try {
      return jpaBuncheolMemberRepository.deleteIfUnreferenced(buncheolMemberId, buncheolId) > 0;
    } catch (DataIntegrityViolationException ex) {
      // NOT EXISTS 가 모르는 참조(새로 생긴 FK)가 자리를 붙잡은 경우다. 어떤 기록이든 자리를 못 빼는 이유는 같다.
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_HISTORY);
    }
  }
}
