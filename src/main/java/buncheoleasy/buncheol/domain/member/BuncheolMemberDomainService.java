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
    validateFreePriceNotMixed(params);

    List<BuncheolMember> newBuncheolMembers =
        params.stream()
            .map(param -> BuncheolMember.create(buncheolId, param.memberId(), param.price()))
            .toList();

    buncheolMemberRepository.saveAll(newBuncheolMembers);
  }

  // 0원(무료) 슬롯은 오픈 이벤트 무료 분철 전용 — 하나라도 0원이면 전 슬롯이 0원이어야 한다.
  // 혼합 구성을 허용하면 목록의 이벤트 배지 판정(전 슬롯 0원)과 배송비 환급 대상 판정(해당 참여 amount == 0)이 어긋난다.
  private void validateFreePriceNotMixed(final List<BuncheolMemberParams> params) {
    boolean hasFreeSlot = params.stream().anyMatch(param -> param.price() == 0L);
    boolean hasPaidSlot = params.stream().anyMatch(param -> param.price() > 0L);
    if (hasFreeSlot && hasPaidSlot) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_FREE_PRICE_MIXED);
    }
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
}
