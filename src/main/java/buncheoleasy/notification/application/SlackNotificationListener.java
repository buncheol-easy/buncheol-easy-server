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

  /** (운영자) 신규 참여 접수. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)라 참여 한 건이 메시지 한 건이다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onParticipationCreated(final ParticipationCreatedEvent event) {
    // 웹훅 미설정 환경(로컬/CI)은 발송하지 않으므로 조립 조회부터 건너뛴다.
    if (!slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)) {
      return;
    }
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    RefundAccount refundAccount = view.participation().getRefundAccount();

    String message =
        """
        🔔 [신규 참여] %s (분철 #%d)
        참여자: %s
        환불계좌: %s %s (예금주 %s)
        멤버: %s
        입금 예정 금액: %s원
        ⏰ 입금 기한: %s (기한 내 입금확인 필요)"""
            .formatted(
                view.buncheol().getTitle(),
                view.buncheol().getId(),
                formatParticipant(view),
                refundAccount.bank(),
                refundAccount.account(),
                refundAccount.holder(),
                view.memberName(),
                AlimtalkFormats.amount(view.paymentAmount()),
                DUE_AT_FORMAT.format(view.participation().getDueAt()));
    slackWebhookClient.send(SlackChannel.NEW_PARTICIPATION, message);
  }

  // 입금내역 대조용으로 실명이 있으면 "닉네임(실명)" 으로 병기한다 (기존 회원은 실명이 없을 수 있음).
  private String formatParticipant(final ParticipationView view) {
    String nickname = view.participant().getNickname().value();
    String name = view.participant().getName();
    return name == null ? nickname : "%s(%s)".formatted(nickname, name);
  }
}
