package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackCompletedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRejectedEvent;
import buncheoleasy.delivery.application.PickupReminderDueEvent;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 받아 알림톡을 발송한다. 원 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행하며, 트랜잭션 없이 발행되는 운송장 등록도 {@code
 * fallbackExecution} 으로 처리한다. 발송 실패는 로깅만 하고 비즈니스에 영향을 주지 않는다.
 *
 * <p>각 핸들러는 알림톡 발송 직전에 {@link NotificationInboxRecorder} 로 in-app 알림(수신함)을 1:1 로 남긴다. 카카오 발송 성공 여부와
 * 무관하게 수신함에서 확인할 수 있도록 발송보다 먼저 기록하되, 기록 실패가 알림톡 발송까지 막지 않도록 {@link #recordSafely} 로 예외를 격리한다(두 채널은
 * 서로 독립적으로 실패할 수 있어야 한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlimtalkNotificationListener {

  private final NotificationAssembler assembler;
  private final AlimtalkSender sender;
  private final NotificationInboxRecorder inboxRecorder;

  /** (참여자) 개최자가 입금을 확인함. 참여가 확정됐다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentConfirmed(final PaymentConfirmedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()));
    recordSafely(view.participant().getId(), AlimtalkTemplate.PAYMENT_CONFIRMED, variables);
    sender.send(
        AlimtalkTemplate.PAYMENT_CONFIRMED, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 입금 기한이 지나 입금 만료 스케줄러가 참여를 자동 취소함. 입금했다면 환불 대상이다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentExpired(final PaymentExpiredEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName());
    recordSafely(view.participant().getId(), AlimtalkTemplate.PAYMENT_EXPIRED, variables);
    sender.send(
        AlimtalkTemplate.PAYMENT_EXPIRED, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 참여한 분철의 진행이 확정됨(최소 인원 충족). */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolConfirmed(final BuncheolConfirmedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName());
    recordSafely(view.participant().getId(), AlimtalkTemplate.BUNCHEOL_CONFIRMED, variables);
    sender.send(
        AlimtalkTemplate.BUNCHEOL_CONFIRMED,
        view.participant().getPhoneNumber().value(),
        variables);
  }

  /** (참여자) 참여한 분철이 취소됨(개최자 취소 또는 최소 인원 미달). 입금했다면 환불 대상이다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "취소사유", event.reason().getDescription());
    recordSafely(view.participant().getId(), AlimtalkTemplate.BUNCHEOL_CANCELLED, variables);
    sender.send(
        AlimtalkTemplate.BUNCHEOL_CANCELLED,
        view.participant().getPhoneNumber().value(),
        variables);
  }

  /** (참여자) 내가 참여한 건의 운송장이 등록됨. 택배사(CU/GS25)에 따라 템플릿이 갈린다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onTrackingRegistered(final TrackingRegisteredEvent event) {
    Delivery delivery = assembler.loadDelivery(event.deliveryId());
    ParticipationView view = assembler.loadByParticipation(delivery.getParticipationId());
    AlimtalkTemplate template =
        switch (delivery.getShippingMethod()) {
          case CU_HALF -> AlimtalkTemplate.TRACKING_CU;
          case GS25_HALF -> AlimtalkTemplate.TRACKING_GS25;
        };
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "운송장번호", delivery.getTrackingNumber());
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 편의점 도착 후 기준 시간이 지나도록 미수령이라 찾아가라고 독촉함. 택배사(CU/GS25)에 따라 템플릿이 갈린다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPickupReminderDue(final PickupReminderDueEvent event) {
    Delivery delivery = assembler.loadDelivery(event.deliveryId());
    ParticipationView view = assembler.loadByParticipation(delivery.getParticipationId());
    AlimtalkTemplate template =
        switch (delivery.getShippingMethod()) {
          case CU_HALF -> AlimtalkTemplate.PICKUP_REMINDER_CU;
          case GS25_HALF -> AlimtalkTemplate.PICKUP_REMINDER_GS25;
        };
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "지점명", delivery.getStoreName(),
            "운송장번호", delivery.getTrackingNumber());
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 운영진이 배송비 환급 입금을 완료함. 환급액은 신청 시점에 스냅샷된 배송비다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onShippingFeePaybackCompleted(final ShippingFeePaybackCompletedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Long paybackAmount = paybackAmountOrNull(view, event.participationId());
    if (paybackAmount == null) {
      return;
    }
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "환급금액", AlimtalkFormats.amount(paybackAmount));
    recordSafely(view.participant().getId(), AlimtalkTemplate.PAYBACK_COMPLETED, variables);
    sender.send(
        AlimtalkTemplate.PAYBACK_COMPLETED,
        view.participant().getPhoneNumber().value(),
        variables);
  }

  /**
   * (참여자) 운영진이 배송비 환급 후기를 반려함. 사유를 보고 기한 내 재신청할 수 있다. 반려 사유는 재신청이 끼어들면 엔티티에서 지워지므로 재조회하지 않고
   * 이벤트 스냅샷을 쓴다(환급액은 재신청해도 같은 배송비 스냅샷이 다시 세팅되므로 재조회해도 안전).
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onShippingFeePaybackRejected(final ShippingFeePaybackRejectedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Long paybackAmount = paybackAmountOrNull(view, event.participationId());
    if (paybackAmount == null) {
      return;
    }
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "반려사유", event.rejectReason(),
            "환급금액", AlimtalkFormats.amount(paybackAmount));
    recordSafely(view.participant().getId(), AlimtalkTemplate.PAYBACK_REJECTED, variables);
    sender.send(
        AlimtalkTemplate.PAYBACK_REJECTED,
        view.participant().getPhoneNumber().value(),
        variables);
  }

  // 환급액 스냅샷은 REQUESTED 선행 전이가 세팅을 보장하지만, 수동 데이터 보정 등으로 비면 Map.of 조립 단계
  // NPE 로 수신함 기록까지 통째로 유실되므로 명시적으로 걸러 로그를 남긴다.
  private Long paybackAmountOrNull(final ParticipationView view, final Long participationId) {
    Long paybackAmount = view.participation().getPaybackAmount();
    if (paybackAmount == null) {
      log.error("환급액 스냅샷이 없어 환급 알림을 건너뜀 - participationId={}", participationId);
    }
    return paybackAmount;
  }

  // in-app 알림 기록 실패가 알림톡 발송을 막지 않도록 격리한다(로깅만). 발송 실패도 비즈니스에 영향을 주지 않는다는 정책과 동일.
  private void recordSafely(
      final Long recipientId, final AlimtalkTemplate template, final Map<String, String> variables) {
    try {
      inboxRecorder.record(recipientId, template, variables);
    } catch (final RuntimeException e) {
      log.error("수신함 알림 기록 실패 - template={}, recipientId={}", template, recipientId, e);
    }
  }
}
