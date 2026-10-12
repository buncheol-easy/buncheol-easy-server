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

  // 자리를 가리키는 FK 전부(schema.sql participations·participation_codes, 둘 다 ON DELETE 없음).
  private static final List<String> SLOT_REFERENCE_FOREIGN_KEYS =
      List.of("fk_participations_buncheol_member", "fk_participation_codes_member");

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
      if (isViolationOf(ex, UNIQUE_BUNCHEOL_MEMBER_INDEX)) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
      }
      throw ex;
    }
  }

  @Override
  public Optional<BuncheolMember> findByBuncheolIdAndMemberId(
      final Long buncheolId, final Long memberId) {
    return jpaBuncheolMemberRepository.findByBuncheolIdAndMemberId(buncheolId, memberId);
  }

  @Override
  public Optional<BuncheolMember> findByIdAndBuncheolIdForShare(
      final Long id, final Long buncheolId) {
    return jpaBuncheolMemberRepository.findByIdAndBuncheolIdForShare(id, buncheolId);
  }

  @Override
  public boolean reopenIfClosed(
      final Long buncheolMemberId, final Long buncheolId, final long price) {
    return jpaBuncheolMemberRepository.reopenIfClosed(
            buncheolMemberId,
            buncheolId,
            price,
            BuncheolMemberAccessType.OPEN,
            BuncheolMemberAccessType.CLOSED)
        > 0;
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
      // 자리를 가리키는 FK 가 막은 경우만 「기록이 있어 못 뺀다」로 바꾼다. DELETE 는 flushAutomatically 라 같은
      // 트랜잭션의 다른 쓰기가 여기서 실패할 수 있는데, 그걸 이 안내로 덮으면 원인이 가려진다 — 그대로 올린다.
      if (SLOT_REFERENCE_FOREIGN_KEYS.stream().anyMatch(fk -> isViolationOf(ex, fk))) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_HISTORY);
      }
      throw ex;
    }
  }

  // MySQL·H2 모두 위반 메시지에 제약·인덱스 이름이 실린다(H2 는 대문자).
  // JpaParticipationRepositoryAdapter#isViolationOf 와 같은 판별이다.
  private static boolean isViolationOf(
      final DataIntegrityViolationException ex, final String constraintName) {
    return String.valueOf(ex.getMessage()).toLowerCase().contains(constraintName);
  }
}
