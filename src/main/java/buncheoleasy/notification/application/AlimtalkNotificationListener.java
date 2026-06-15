package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.ParticipationWonEvent;
import buncheoleasy.buncheol.application.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.PaymentDueImminentEvent;
import buncheoleasy.buncheol.application.PaymentReportedEvent;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.notification.infrastructure.DueReminderGuard;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 받아 알림톡을 발송한다. 원 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행하며, 트랜잭션 없이 발행되는 운송장 등록도
 * {@code fallbackExecution} 으로 처리한다. 발송 실패는 로깅만 하고 비즈니스에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlimtalkNotificationListener {

  private final NotificationAssembler assembler;
  private final AlimtalkSender sender;
  private final DueReminderGuard dueReminderGuard;

  /** (개최자) 참여자가 입금 확인 요청을 함. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentReported(final PaymentReportedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.host().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "참여자닉네임", view.participant().getNickname().value(),
            "멤버명", view.memberName(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()),
            "신고시각", AlimtalkFormats.dateTime(view.participation().getPaymentReportedAt()),
            "분철아이디", String.valueOf(view.buncheol().getId()));
    sender.send(AlimtalkTemplate.PAYMENT_REPORTED, view.host().getPhoneNumber().value(), variables);
  }

  /** (참여자) 입금이 확인됨. */
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
    sender.send(
        AlimtalkTemplate.PAYMENT_CONFIRMED, view.participant().getPhoneNumber().value(), variables);
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
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 참여한 분철에 낙찰됨 / 차순위 이양으로 새로 낙찰됨. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipationWon(final ParticipationWonEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()),
            "입금기한", AlimtalkFormats.dateTime(view.participation().getDueAt()));
    sender.send(
        AlimtalkTemplate.PARTICIPATION_WON, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 참여한 분철이 개최자에 의해 취소됨. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName());
    sender.send(
        AlimtalkTemplate.BUNCHEOL_CANCELLED, view.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 입금 기한 임박. 중복 발송은 Redis 가드로 막는다(폴링 반복·다중 인스턴스 대비). */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentDueImminent(final PaymentDueImminentEvent event) {
    if (!dueReminderGuard.tryMark(event.participationId())) {
      return;
    }
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()),
            "입금기한", AlimtalkFormats.dateTime(view.participation().getDueAt()));
    sender.send(
        AlimtalkTemplate.PAYMENT_DUE_IMMINENT,
        view.participant().getPhoneNumber().value(),
        variables);
  }
}
