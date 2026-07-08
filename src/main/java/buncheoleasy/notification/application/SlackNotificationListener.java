package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 받아 운영자 슬랙 채널로 알린다. MVP 는 개최자가 운영자뿐이므로, 신규 참여가 생기면 운영자가 입금 기한(기본 30분) 내에 확인·입금확인할 수 있도록
 * 즉시 알린다. 원 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되며 발송 실패는 로깅만 하고 비즈니스에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SlackNotificationListener {

  private static final DateTimeFormatter DUE_AT_FORMAT =
      DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.of("Asia/Seoul"));

  private final NotificationAssembler assembler;
  private final SlackWebhookClient slackWebhookClient;

  /** (운영자) 신규 참여 접수. 한 참여 요청의 슬롯 묶음을 메시지 한 건으로 합쳐 보낸다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onParticipationCreated(final ParticipationCreatedEvent event) {
    // 웹훅 미설정 환경(로컬/CI)은 발송하지 않으므로 조립 조회부터 건너뛴다.
    if (!slackWebhookClient.isEnabled(SlackChannel.OPERATION)) {
      return;
    }
    ParticipationBundleView view = assembler.loadByParticipations(event.participationIds());
    RefundAccount refundAccount = view.refundAccount();

    String message =
        """
        🔔 [신규 참여] %s (분철 #%d)
        참여자: %s
        환불계좌: %s %s (예금주 %s)
        멤버: %s (%d슬롯)
        입금 예정 금액: %s원
        ⏰ 입금 기한: %s (기한 내 입금확인 필요)"""
            .formatted(
                view.buncheol().getTitle(),
                view.buncheol().getId(),
                view.participant().getNickname().value(),
                refundAccount.bank(),
                refundAccount.account(),
                refundAccount.holder(),
                String.join(", ", view.memberNames()),
                view.memberNames().size(),
                AlimtalkFormats.amount(view.totalAmount()),
                DUE_AT_FORMAT.format(view.dueAt()));
    slackWebhookClient.send(SlackChannel.OPERATION, message);
  }
}
