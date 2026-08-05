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
    // 가입 미완료 유저의 배송지 등록 차단 (내부 getUser 가 USER_NOT_FOUND 검증도 겸한다).
    // 등록만 강화하고 수정/삭제/조회는 validateUser 를 유지한다 — profileCompleted 는 비가역
    // true 전이라 미완료 유저는 배송지를 만들 수 없고, 따라서 수정/삭제할 대상도 없다.
    userDomainService.requireProfileCompleted(userId);
    shippingAddressDomainService.createShippingAddress(
        userId,
        request.shippingMethod(),
        request.storeName(),
        request.storeCode(),
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
        request.storeCode(),
        request.alias(),
        request.isDefault());
  }

  public void removeShippingAddress(final Long userId, final Long addressId) {
    validateUser(userId);
    // 소유권을 먼저 검증한다 — 참여 참조 가드(409)를 소유권(403)보다 먼저 태우면, 남의 배송지 id 로도 그 배송지가 참여에
    // 쓰이는지 여부가 응답 코드로 새어나간다.
    shippingAddressDomainService.validateOwnership(userId, addressId);
    // 활성(입금대기중·입금확인됨) 참여가 이 배송지를 참조하면 삭제를 막는다. 종료(취소·만료)된 참여만 참조하는 배송지는
    // 삭제를 허용하고, FK ON DELETE SET NULL 이 그 종료 참여들의 배송지값만 NULL 로 정리한다.
    // 주의: 이 가드(read)와 아래 delete 커밋 사이에 같은 유저가 동일 배송지로 새 참여를 생성하는 밀리초 창이 있다.
    // 그 창에 걸리면 FK SET NULL 이 방금 생긴 활성 참여의 배송지까지 NULL 로 만들어 입금확인이 막힐 수 있다
    // (동일 유저 동시요청 한정, 확률 매우 낮음). 완전 차단이 필요하면 FK RESTRICT + 종료참여 명시 NULL + FK위반 catch 로 전환.
    if (participationDomainService.hasActiveParticipationByShippingAddress(addressId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DELETE_BLOCKED_BY_ACTIVE_PARTICIPATION);
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
        shippingAddress.getStoreCode(),
        shippingAddress.getAlias(),
        shippingAddress.isDefault());
  }
}
