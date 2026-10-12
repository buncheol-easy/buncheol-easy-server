package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolMemberDomainService {

  private final BuncheolMemberRepository buncheolMemberRepository;

  public void createBuncheolMembers(
      final Long buncheolId, final List<BuncheolMemberParams> params) {
    if (params == null || params.isEmpty()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_REQUIRED);
    }

    List<BuncheolMember> newBuncheolMembers =
        params.stream()
            .map(
                param ->
                    BuncheolMember.create(
                        buncheolId, param.memberId(), param.price(), param.accessType()))
            .toList();

    buncheolMemberRepository.saveAll(newBuncheolMembers);
  }

  public void deleteAllByBuncheolId(final Long buncheolId) {
    buncheolMemberRepository.deleteAllByBuncheolId(buncheolId);
  }

  public BuncheolMember getBuncheolMember(final Long id, final Long buncheolId) {
    return buncheolMemberRepository
        .findByIdAndBuncheolId(id, buncheolId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND));
  }

  public List<BuncheolMember> findAllByBuncheolId(final Long buncheolId) {
    return buncheolMemberRepository.findAllByBuncheolId(buncheolId);
  }

  public List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(final Long buncheolId) {
    return buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);
  }

  public void deleteById(final Long id) {
    buncheolMemberRepository.deleteById(id);
  }

  /** 개최 때와 같은 규칙(가격 100원 단위·0 이상)으로 선착순 자리 하나를 만든다. 중복 멤버는 저장 시점에 거부된다. */
  public BuncheolMember addOpenSlot(final Long buncheolId, final Long memberId, final long price) {
    return buncheolMemberRepository.save(BuncheolMember.create(buncheolId, memberId, price));
  }

  public boolean existsInBuncheol(final Long buncheolId, final Long memberId) {
    return buncheolMemberRepository.existsByBuncheolIdAndMemberId(buncheolId, memberId);
  }

  /** 분철의 자리를 잠금 조회한다 — 포트 javadoc 참고. 호출 측 {@code @Transactional} 필수. */
  public List<BuncheolMember> findAllByBuncheolIdForUpdate(final Long buncheolId) {
    return buncheolMemberRepository.findAllByBuncheolIdForUpdate(buncheolId);
  }

  /**
   * 참여·참여 코드 기록이 하나도 없는 자리만 지운다 — 판정과 삭제를 한 DELETE 로 원자화한다. 호출 측 {@code @Transactional}
   * 필수.
   *
   * @return 지웠으면 true
   */
  public boolean removeIfUnreferenced(final Long buncheolMemberId, final Long buncheolId) {
    return buncheolMemberRepository.deleteIfUnreferenced(buncheolMemberId, buncheolId);
  }

  /** 활성 참여가 있는 슬롯은 바꾸지 않는다 — 판정과 전이를 한 UPDATE 로 원자화한다. */
  public void changeAccessType(
      final Long buncheolMemberId, final Long buncheolId, final BuncheolMemberAccessType accessType) {
    if (!buncheolMemberRepository.changeAccessTypeIfUnoccupied(
        buncheolMemberId, buncheolId, accessType)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_ACCESS_TYPE_CHANGE_NOT_ALLOWED);
    }
  }
}
