package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.BuncheolCollectingStartedEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.BuncheolFullEvent;
import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.participation.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.application.participation.PaymentRecheckRequestedEvent;
import buncheoleasy.buncheol.application.participation.PaymentSentEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackCompletedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRejectedEvent;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.application.PickupReminderDueEvent;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 받아 알림톡을 발송한다. 원 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행하며, 혹시 트랜잭션 밖에서 발행되는 이벤트도 {@code
 * fallbackExecution} 으로 놓치지 않는다. 발송 실패는 로깅만 하고 비즈니스에 영향을 주지 않는다.
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

  /**
   * (참여자) 입금 기한이 지나 입금 만료 스케줄러가 참여를 자동 취소함. LEGACY 는 플랫폼 환불 안내, C2C 는 돈이 개최자 계좌로 가는 직거래라 문의
   * 유도 문안으로 갈린다 (docs/46 §6.2).
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentExpired(final PaymentExpiredEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    AlimtalkTemplate template =
        view.buncheol().isC2c()
            ? AlimtalkTemplate.C2C_PAYMENT_EXPIRED
            : AlimtalkTemplate.PAYMENT_EXPIRED;
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName());
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
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

  /**
   * (참여자) 참여한 분철이 취소됨(개최자 취소·미달·C2C 미성사). C2C 무입금 단계(입금확인·보냈어요 이력 없음)는 환불이 없다는 미성사 문안으로,
   * 입금 이력이 있으면 기존 환불 안내 문안으로 보낸다 — C2C 의 환불 주체는 개최자다(직거래).
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    boolean c2cNoPayment =
        view.buncheol().isC2c()
            && view.participation().getConfirmedAt() == null
            && view.participation().getPaymentSentAt() == null;
    AlimtalkTemplate template =
        c2cNoPayment ? AlimtalkTemplate.C2C_BUNCHEOL_NOT_FINALIZED : AlimtalkTemplate.BUNCHEOL_CANCELLED;
    Map<String, String> variables =
        c2cNoPayment
            ? Map.of(
                "닉네임", view.participant().getNickname().value(),
                "분철명", view.buncheol().getTitle(),
                "멤버명", view.memberName())
            : Map.of(
                "닉네임", view.participant().getNickname().value(),
                "분철명", view.buncheol().getTitle(),
                "멤버명", view.memberName(),
                "취소사유", event.reason().getDescription());
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) C2C 분철 성사 확정 — 입금 안내를 유저 단위로 합산해 1건씩 발송한다 (docs/46 §4.7-A3). */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCollectingStarted(final BuncheolCollectingStartedEvent event) {
    // 커밋 시점 전이 대상 스냅샷(event.participationIds)만 발송한다 — 실행 시점 상태 재조회는 그 사이 들어온
    // 추가 모집 참여(onParticipationCreated 로 개별 안내를 받음)가 섞여 중복 발송된다. 커밋~실행 사이에
    // 취소·확정된 건은 입금 안내가 무의미하므로 상태 필터로 거른다.
    List<ParticipationView> views =
        event.participationIds().stream()
            .map(assembler::loadByParticipation)
            .filter(
                view ->
                    view.participation().getStatus() == ParticipationStatus.AWAITING_PAYMENT
                        || view.participation().getStatus() == ParticipationStatus.PAYMENT_SENT)
            .toList();
    Map<Long, List<ParticipationView>> byParticipant =
        views.stream()
            .collect(
                Collectors.groupingBy(
                    view -> view.participant().getId(), LinkedHashMap::new, Collectors.toList()));
    byParticipant.values().forEach(this::sendFinalizedNotice);
  }

  /** (참여자) C2C 추가 모집 즉시입금 진입 — 이미 성사된 분철의 빈 슬롯 참여라 입금 안내를 바로 보낸다 (docs/46 §4.7-E1). */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipationCreated(final ParticipationCreatedEvent event) {
    // LEGACY 신규 참여는 알림톡 없음(현행 유지 — 계좌는 응답으로 안내, 운영 관제는 슬랙). C2C 무입금 신청(APPLIED)도 발송 없음.
    if (event.flowType() != FlowType.C2C) {
      return;
    }
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    if (view.participation().getStatus() != ParticipationStatus.AWAITING_PAYMENT) {
      return;
    }
    sendC2cPaymentGuide(
        view,
        AlimtalkTemplate.C2C_BUNCHEOL_FINALIZED,
        view.memberName(),
        view.paymentAmount(),
        view.participation().getDueAt());
  }

  /** (개최자) C2C 모집 정원 충족 — 분철 관리에서 진행 확정을 눌러달라고 독촉한다. 미충족→충족 전이마다 발송된다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolFull(final BuncheolFullEvent event) {
    BuncheolHostView view = assembler.loadBuncheolHost(event.buncheolId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.host().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "신청인원", String.valueOf(event.appliedCount()),
            // 본문엔 없고 버튼 링크·수신함 경로(분철 관리 화면)에서만 치환되는 변수.
            "분철ID", String.valueOf(view.buncheol().getId()));
    recordSafely(view.host().getId(), AlimtalkTemplate.C2C_BUNCHEOL_FULL, variables);
    sender.send(
        AlimtalkTemplate.C2C_BUNCHEOL_FULL, view.host().getPhoneNumber().value(), variables);
  }

  /**
   * (개최자) C2C 참여자가 '보냈어요' 를 누름 — 통장을 확인하고 입금 확인(또는 반려)해 달라고 요청한다. 마킹(슬롯) 건당 1건이라 다슬롯 참여자는
   * 슬롯 수만큼 발송될 수 있고, 금액도 슬롯별 금액이다 — 이체 1건과의 대조는 입금자명(환불 계좌 예금주, docs/46 §4.7-A1)으로 한다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentSent(final PaymentSentEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.host().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "참여자닉네임", view.participant().getNickname().value(),
            "입금자명", view.participation().getRefundAccount().holder(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()),
            "분철ID", String.valueOf(view.buncheol().getId()));
    recordSafely(view.host().getId(), AlimtalkTemplate.C2C_PAYMENT_SENT, variables);
    sender.send(
        AlimtalkTemplate.C2C_PAYMENT_SENT, view.host().getPhoneNumber().value(), variables);
  }

  /** (참여자) C2C 개최자가 "보냈어요" 를 반려함 — 연장된 새 기한을 담아 입금 재확인을 안내한다 (docs/46 §4.5). */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentRecheckRequested(final PaymentRecheckRequestedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    sendC2cPaymentGuide(
        view,
        AlimtalkTemplate.C2C_PAYMENT_RECHECK,
        view.memberName(),
        view.paymentAmount(),
        view.participation().getDueAt());
  }

  // 다슬롯 참여자에게는 멤버명 "호시 외 1"·금액 합산으로 1건만 보낸다. 계좌·기한은 분철의 확정 시점 스냅샷 (docs/46 §4.7-B1).
  private void sendFinalizedNotice(final List<ParticipationView> group) {
    ParticipationView first = group.get(0);
    long totalAmount = group.stream().mapToLong(ParticipationView::paymentAmount).sum();
    String memberName =
        group.size() == 1
            ? first.memberName()
            : first.memberName() + " 외 " + (group.size() - 1);
    sendC2cPaymentGuide(
        first,
        AlimtalkTemplate.C2C_BUNCHEOL_FINALIZED,
        memberName,
        totalAmount,
        first.buncheol().getPaymentDueAt());
  }

  // C2C 입금 안내 계열(성사 확정·추가 모집·재확인) 공용 발송. 계좌·기한 스냅샷이 비면 오안내를 막기 위해 건너뛰고 로그만 남긴다.
  private void sendC2cPaymentGuide(
      final ParticipationView view,
      final AlimtalkTemplate template,
      final String memberName,
      final long amount,
      final Instant dueAt) {
    BankAccount account = view.buncheol().getPaymentAccount();
    if (account == null || dueAt == null) {
      log.error(
          "C2C 계좌·기한 스냅샷이 없어 입금 안내를 건너뜀 - buncheolId={}, participationId={}",
          view.buncheol().getId(),
          view.participation().getId());
      return;
    }
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", memberName,
            "입금금액", AlimtalkFormats.amount(amount),
            "은행명", account.bank(),
            "계좌번호", account.account(),
            "예금주", account.holder(),
            "입금기한", AlimtalkFormats.dueAt(dueAt));
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
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
