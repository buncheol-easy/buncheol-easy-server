package buncheoleasy.user.application;

import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.dto.request.ShippingAddressRequest;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;
  private final ParticipationDomainService participationDomainService;

  public void registerShippingAddress(final Long userId, final ShippingAddressRequest request) {
    validateUser(userId);
    shippingAddressDomainService.createShippingAddress(
        userId,
        request.shippingMethod(),
        request.storeName(),
        request.alias(),
        request.isDefault());
  }

  public void modifyShippingAddress(
      final Long userId, final Long addressId, final ShippingAddressRequest request) {
    validateUser(userId);
    shippingAddressDomainService.updateShippingAddress(
        userId,
        addressId,
        request.shippingMethod(),
        request.storeName(),
        request.alias(),
        request.isDefault());
  }

  public void removeShippingAddress(final Long userId, final Long addressId) {
    validateUser(userId);
    // 참여가 이 배송지를 참조하면(취소·만료 포함) 삭제를 막는다. participations.shipping_address_id FK 가 RESTRICT 라
    // 참조 중인 배송지를 지우면 DB 제약 위반(500)이 나므로 그 전에 409 로 거절한다. 취소 참여만 참조하는 배송지도 이력
    // 보존을 위해 삭제 불가로 둔다(삭제 허용 시 참여 이력의 배송지 정보 소실 — 이력 정책은 별도 스코프에서 재검토).
    if (participationDomainService.hasParticipationByShippingAddress(addressId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DELETE_BLOCKED_BY_PARTICIPATION);
    }
    shippingAddressDomainService.deleteShippingAddress(userId, addressId);
  }

  public List<ShippingAddressResponse> getUserShippingAddresses(final Long userId) {
    validateUser(userId);
    return shippingAddressDomainService.getUserShippingAddresses(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  private void validateUser(final Long userId) {
    if (!userDomainService.isValidUser(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  private ShippingAddressResponse toResponse(final ShippingAddress shippingAddress) {
    return ShippingAddressResponse.of(
        shippingAddress.getId(),
        shippingAddress.getShippingMethod().name(),
        shippingAddress.getStoreName(),
        shippingAddress.getAlias(),
        shippingAddress.isDefault());
  }
}
