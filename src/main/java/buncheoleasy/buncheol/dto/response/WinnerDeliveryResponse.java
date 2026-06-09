package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;

/**
 * 개최자 분철 관리 화면의 옵션별 낙찰자 결제·배송 현황.
 *
 * <p>현재 결제 대상(낙찰자) 1명의 결제 진행 상태와, 입금확인(CONFIRMED) 이후 찍히는 배송 스냅샷을 함께 담는다. 슬롯당 최대 1명만 노출되며, 노출 대상 상태는
 * {@code AWAITING_PAYMENT / PAYMENT_REPORTED / CONFIRMED} 다. ACTIVE_BID 상태의 차순위 후보는 아직 결제 대상이 아니라 노출하지 않는다.
 *
 * <p>결제 필드({@code participationId}~{@code paymentConfirmedAt})는 라이브 상태값이며, {@code participationId} 는 개최자
 * 수동 입금확인 API 호출에 사용한다. 배송 필드({@code deliveryId}~{@code deliveryStatus})는 CONFIRMED 시점 스냅샷이라 그 전에는 모두
 * null 이고, 이후 낙찰자가 닉네임·배송지를 바꿔도 영향받지 않는다. 개최자 계좌 정보는 본 응답에 포함하지 않는다.
 *
 * @param participationId 낙찰자 참여 ID. 개최자 입금확인 API 의 대상 식별자
 * @param paymentStatus 결제 진행 상태 (AWAITING_PAYMENT / PAYMENT_REPORTED / CONFIRMED)
 * @param bidAmount 낙찰가. 개최자가 실제 입금액 대조에 사용
 * @param paymentDueAt 입금 기한. 미설정 시 null
 * @param paymentReportedAt 구매자 입금완료 신고 시각. 미신고 시 null
 * @param paymentConfirmedAt 개최자 입금확인 시각. 미확인 시 null
 * @param trackingNumber 호스트가 등록한 운송장 번호. 미등록 시 null
 */
public record WinnerDeliveryResponse(
    Long participationId,
    ParticipationStatus paymentStatus,
    Long bidAmount,
    Instant paymentDueAt,
    Instant paymentReportedAt,
    Instant paymentConfirmedAt,
    Long deliveryId,
    ShippingMethod shippingMethod,
    String storeName,
    String receiverNickname,
    String receiverPhoneNumber,
    String trackingNumber,
    DeliveryStatus deliveryStatus) {}
