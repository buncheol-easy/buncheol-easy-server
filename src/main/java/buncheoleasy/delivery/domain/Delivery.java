package buncheoleasy.delivery.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참여 확정 시점에 생성되는 배송 스냅샷. 수동 경로와 추적 웹훅 자동 전이가 경합하므로 상태 전이는 리포지토리의 status 조건부 CAS UPDATE 로만 하고,
 * 엔티티는 조회용 홀더로 둔다(전이 도메인 메서드 없음).
 */
@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "participation_id", nullable = false, updatable = false)
  private Long participationId;

  // 분철 마감 시점에 찍은 배송 방법 스냅샷 (이후 사용자가 배송지 변경해도 불변).
  @Enumerated(EnumType.STRING)
  @Column(name = "shipping_method", nullable = false, length = 20, updatable = false)
  private ShippingMethod shippingMethod;

  // 분철 마감 시점에 찍은 편의점 지점명 스냅샷.
  @Column(name = "store_name", nullable = false, length = 100, updatable = false)
  private String storeName;

  // 분철 마감 시점에 찍은 수령인 닉네임 스냅샷 (이후 유저가 닉네임 변경해도 불변).
  @Column(name = "receiver_nickname", nullable = false, length = 20, updatable = false)
  private String receiverNickname;

  // 분철 마감 시점에 찍은 수령인 전화번호 스냅샷.
  @Column(name = "receiver_phone_number", nullable = false, length = 15, updatable = false)
  private String receiverPhoneNumber;

  // 호스트가 등록한 운송장 번호. 등록 전에는 NULL.
  @Column(name = "tracking_number", length = 100)
  private String trackingNumber;

  // 호스트가 운송장을 등록한 시각 (SNAPSHOTTED → SHIPPING 진입 시각).
  @Column(name = "tracking_registered_at")
  private Instant trackingRegisteredAt;

  // 운송사 추적으로 배송 완료가 감지된 시각 (SHIPPING → DELIVERED 자동 전이).
  @Column(name = "delivered_at")
  private Instant deliveredAt;

  // 참여자가 직접 수령 확인 버튼을 눌러 RECEIVED 로 전이된 시각.
  @Column(name = "received_at")
  private Instant receivedAt;

  // 지점 도착 후 미수령 독촉 알림을 보낸 시각 (1회 발송 dedup 마커, 세팅은 CAS 로만).
  @Column(name = "pickup_reminder_sent_at")
  private Instant pickupReminderSentAt;

  // SNAPSHOTTED | SHIPPING | DELIVERED | RECEIVED.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeliveryStatus status;

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
