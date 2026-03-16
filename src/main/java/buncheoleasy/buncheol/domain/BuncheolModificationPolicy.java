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

  /** 참여 유형에 따른 멤버 가격 수정 제약 검증 */
  public void validateMemberPricingChange(
      final BuncheolMember existing,
      final MemberParticipationPresence presence,
      final long newInstantPrice,
      final boolean newBidAllowed,
      final Long newBidMinPrice) {
    if (presence == null) {
      return;
    }
    if (presence.hasActiveInstant()) {
      validateInstantMemberUnchanged(existing, newInstantPrice, newBidAllowed, newBidMinPrice);
    } else if (presence.hasActiveBid()) {
      validateBidOnlyMemberChange(existing, newBidAllowed, newBidMinPrice);
    }
  }

  private void validateLockedFields(final Buncheol buncheol, final BuncheolParams params) {
    boolean groupChanged =
        !Objects.equals(buncheol.getGroupId(), params.groupId())
            || !Objects.equals(buncheol.getGroupName(), params.groupName());
    boolean goodsInfoChanged =
        !Objects.equals(buncheol.getGoodsName(), params.goodsName())
            || !Objects.equals(buncheol.getStoreName(), params.storeName())
            || buncheol.getOriginalPrice() != params.originalPrice();
    boolean shippingDeadlineChanged =
        buncheol.getShippingDeadlineDays() != params.shippingDeadlineDays();

    if (groupChanged || goodsInfoChanged || shippingDeadlineChanged) {
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

  private void validateInstantMemberUnchanged(
      final BuncheolMember existing,
      final long newInstantPrice,
      final boolean newBidAllowed,
      final Long newBidMinPrice) {
    if (existing.getInstantPrice() != newInstantPrice
        || existing.getBidOption().bidAllowed() != newBidAllowed
        || !Objects.equals(existing.getBidOption().bidMinPrice(), newBidMinPrice)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_PRICE_LOCKED);
    }
  }

  private void validateBidOnlyMemberChange(
      final BuncheolMember existing, final boolean newBidAllowed, final Long newBidMinPrice) {
    if (existing.getBidOption().bidAllowed() && !newBidAllowed) { // 활성 제시 참여가 존재하는데 제시를 불허할 수 없음
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_BID_DISABLE_LOCKED);
    }
    if (existing.getBidOption().bidAllowed()
        && newBidMinPrice != null
        && existing.getBidOption().bidMinPrice() != null
        && newBidMinPrice > existing.getBidOption().bidMinPrice()) { // 제시 최소금액을 높일 수 없음 (내리기만 가능)
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_BID_MIN_INCREASE_LOCKED);
    }
  }
}
