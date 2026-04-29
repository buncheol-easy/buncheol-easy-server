package buncheoleasy.buncheol.domain;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BuncheolModificationPolicy {

  /** 참여자가 존재하는 분철의 수정 불가 필드 및 배송비 변경 검증 */
  public void validateBuncheolFieldChange(
      final Buncheol buncheol,
      final BuncheolParams requestedState,
      final Set<ShippingMethod> usedShippingMethods) {
    validateLockedFields(buncheol, requestedState);
    validateShippingFeeChange(buncheol, requestedState, usedShippingMethods);
  }

  /** 활성 참여가 있는 멤버의 삭제 가능 여부 검증 */
  public void validateMemberDeletion(final MemberParticipationPresence presence) {
    if (presence != null) { // null이 아니면 활성 참여가 존재한다는 의미
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DELETE_LOCKED);
    }
  }

  /** 활성 참여가 있는 멤버의 제시 최소 금액 변경 제약 검증 */
  public void validateMemberPricingChange(
      final BuncheolMember existing,
      final MemberParticipationPresence presence,
      final long newBidMinPrice) {
    if (presence == null || !presence.hasActiveBid()) {
      return;
    }
    // 활성 참여가 있는 동안엔 bid_min_price 를 올릴 수 없음 (내리기만 가능)
    if (newBidMinPrice > existing.getBidMinPrice()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_BID_MIN_INCREASE_LOCKED);
    }
  }

  private void validateLockedFields(final Buncheol buncheol, final BuncheolParams params) {
    boolean groupChanged =
        !Objects.equals(buncheol.getGroupId(), params.groupId())
            || !Objects.equals(buncheol.getGroupName(), params.groupName());
    boolean storeChanged = !Objects.equals(buncheol.getStoreName(), params.storeName());
    boolean shippingDeadlineChanged =
        buncheol.getShippingDeadlineDays() != params.shippingDeadlineDays();

    if (groupChanged || storeChanged || shippingDeadlineChanged) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_FIELD_LOCKED);
    }
  }

  private void validateShippingFeeChange(
      final Buncheol buncheol,
      final BuncheolParams params,
      final Set<ShippingMethod> usedShippingMethods) {
    if (usedShippingMethods.contains(ShippingMethod.GS25_HALF)
        && !Objects.equals(
            buncheol.getShippingFeePolicy().gs25ShippingFee(), params.gs25ShippingFee())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }
    if (usedShippingMethods.contains(ShippingMethod.CU_HALF)
        && !Objects.equals(
            buncheol.getShippingFeePolicy().cuShippingFee(), params.cuShippingFee())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }
  }
}
