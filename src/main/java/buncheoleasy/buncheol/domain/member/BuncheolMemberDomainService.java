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

  public void deleteById(final Long id) {
    buncheolMemberRepository.deleteById(id);
  }

  /** 활성 참여가 있는 슬롯은 바꾸지 않는다 — 판정과 전이를 한 UPDATE 로 원자화한다. */
  public void changeAccessType(
      final Long buncheolMemberId, final Long buncheolId, final SlotAccessType accessType) {
    if (!buncheolMemberRepository.changeAccessTypeIfUnoccupied(
        buncheolMemberId, buncheolId, accessType)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_ACCESS_TYPE_CHANGE_NOT_ALLOWED);
    }
  }
}
