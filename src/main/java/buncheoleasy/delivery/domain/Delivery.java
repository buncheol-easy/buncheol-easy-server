package buncheoleasy.delivery.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class Delivery {

  private Long id;
  private final Long participationId;
  private final ShippingMethod shippingMethod;
  private final String storeName;
  private final String receiverNickname;
  private final String receiverPhoneNumber;
  private String trackingNumber;
  private LocalDateTime trackingRegisteredAt;
  private LocalDateTime deliveredAt;
  private LocalDateTime receivedAt;
  private DeliveryStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Delivery createSnapshot(
      final Long participationId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final String receiverNickname,
      final String receiverPhoneNumber) {
    return new Delivery(
        participationId, shippingMethod, storeName, receiverNickname, receiverPhoneNumber);
  }

  private Delivery(
      final Long participationId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final String receiverNickname,
      final String receiverPhoneNumber) {
    validateSnapshot(
        participationId, shippingMethod, storeName, receiverNickname, receiverPhoneNumber);
    this.participationId = participationId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
    this.receiverNickname = receiverNickname;
    this.receiverPhoneNumber = receiverPhoneNumber;
    this.status = DeliveryStatus.SNAPSHOTTED;
  }

  // MyBatis 조회 전용 생성자
  private Delivery(
      final Long id,
      final Long participationId,
      final String shippingMethod,
      final String storeName,
      final String receiverNickname,
      final String receiverPhoneNumber,
      final String trackingNumber,
      final LocalDateTime trackingRegisteredAt,
      final LocalDateTime deliveredAt,
      final LocalDateTime receivedAt,
      final DeliveryStatus status,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.participationId = participationId;
    this.shippingMethod = ShippingMethod.of(shippingMethod);
    this.storeName = storeName;
    this.receiverNickname = receiverNickname;
    this.receiverPhoneNumber = receiverPhoneNumber;
    this.trackingNumber = trackingNumber;
    this.trackingRegisteredAt = trackingRegisteredAt;
    this.deliveredAt = deliveredAt;
    this.receivedAt = receivedAt;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void registerTracking(final String trackingNumber, final LocalDateTime now) {
    if (trackingNumber == null || trackingNumber.isBlank()) {
      throw new BusinessException(ErrorCode.DELIVERY_TRACKING_NUMBER_REQUIRED);
    }
    if (status == DeliveryStatus.SHIPPING) {
      // 이미 SHIPPING이면 운송장 번호만 업데이트
      this.trackingNumber = trackingNumber;
      this.trackingRegisteredAt = now;
      return;
    }
    if (status != DeliveryStatus.SNAPSHOTTED) {
      throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
    this.trackingNumber = trackingNumber;
    this.trackingRegisteredAt = now;
    this.status = DeliveryStatus.SHIPPING;
  }

  public void confirmReceipt(final LocalDateTime now) {
    if (status != DeliveryStatus.SHIPPING && status != DeliveryStatus.DELIVERED) {
      throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
    this.receivedAt = now;
    this.status = DeliveryStatus.RECEIVED;
  }

  private void validateSnapshot(
      final Long participationId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final String receiverNickname,
      final String receiverPhoneNumber) {
    if (participationId == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND);
    }
    if (shippingMethod == null) {
      throw new BusinessException(ErrorCode.DELIVERY_SHIPPING_METHOD_REQUIRED);
    }
    if (storeName == null || storeName.isBlank()) {
      throw new BusinessException(ErrorCode.DELIVERY_STORE_NAME_REQUIRED);
    }
    if (receiverNickname == null || receiverNickname.isBlank()) {
      throw new BusinessException(ErrorCode.DELIVERY_RECEIVER_NICKNAME_REQUIRED);
    }
    if (receiverPhoneNumber == null || receiverPhoneNumber.isBlank()) {
      throw new BusinessException(ErrorCode.DELIVERY_RECEIVER_PHONE_REQUIRED);
    }
  }
}
