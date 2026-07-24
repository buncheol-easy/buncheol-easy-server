package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRequestedEvent;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 받아 운영자 슬랙 채널로 알린다. MVP 는 개최자가 운영자뿐이므로, 신규 참여가 생기면 운영자가 입금 기한(기본 30분) 내에 확인·입금확인할 수 있도록
 * 즉시 알린다. 원 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되며 발송 실패는 로깅만 하고 비즈니스에 영향을 주지 않는다.
 */
@Component
public class SlackNotificationListener {

  private static final DateTimeFormatter DUE_AT_FORMAT =
      DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.of("Asia/Seoul"));

  private final NotificationAssembler assembler;
  private final SlackWebhookClient slackWebhookClient;
  // 배송비 환급 알림의 어드민 검수 페이지 링크 base (프론트 웹앱 origin).
  private final String frontendBaseUrl;

  public SlackNotificationListener(
      final NotificationAssembler assembler,
      final SlackWebhookClient slackWebhookClient,
      @Value("${app.frontend.base-url}") final String frontendBaseUrl) {
    this.assembler = assembler;
    this.slackWebhookClient = slackWebhookClient;
    this.frontendBaseUrl = frontendBaseUrl;
  }

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

  /**
   * (운영자) 배송비 환급(배송비 돌려받기) 신청 접수. 재신청도 같은 이벤트로 들어와 다시 알린다. 검수에 필요한 정보(신청자·분철·환급액·환불계좌·후기
   * 트윗 링크)를 Block Kit 으로 조립한다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onShippingFeePaybackRequested(final ShippingFeePaybackRequestedEvent event) {
    if (!slackWebhookClient.isEnabled(SlackChannel.SHIPPING_FEE_PAYBACK)) {
      return;
    }
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    RefundAccount refundAccount = view.participation().getRefundAccount();
    Long paybackAmount = view.participation().getPaybackAmount();
    String tweetUrl = view.participation().getPaybackTweetUrl();

    String fallbackText =
        "💸 [배송비 돌려받기 신청] %s (참여 #%d) - %s"
            .formatted(
                view.buncheol().getTitle(), event.participationId(), formatParticipant(view));
    List<Map<String, Object>> blocks =
        List.of(
            Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "💸 배송비 돌려받기 신청")),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    markdown("*신청자*\n%s".formatted(formatParticipant(view))),
                    markdown(
                        "*분철*\n%s (#%d)"
                            .formatted(view.buncheol().getTitle(), view.buncheol().getId())),
                    markdown(
                        "*환급액*\n%s원"
                            .formatted(
                                AlimtalkFormats.amount(
                                    paybackAmount == null ? 0L : paybackAmount))),
                    markdown(
                        "*환불계좌*\n%s %s (예금주 %s)"
                            .formatted(
                                refundAccount.bank(),
                                refundAccount.account(),
                                refundAccount.holder())))),
            Map.of(
                "type",
                "section",
                "text",
                markdown(
                    "<%s|후기 트윗 보기> · <%s/admin|어드민에서 처리>"
                        .formatted(tweetUrl, frontendBaseUrl))));
    slackWebhookClient.send(SlackChannel.SHIPPING_FEE_PAYBACK, fallbackText, blocks);
  }

  private static Map<String, Object> markdown(final String text) {
    return Map.of("type", "mrkdwn", "text", text);
  }

  // 입금내역 대조용으로 실명이 있으면 "닉네임(실명)" 으로 병기한다 (기존 회원은 실명이 없을 수 있음).
  private String formatParticipant(final ParticipationView view) {
    String nickname = view.participant().getNickname().value();
    String name = view.participant().getName();
    return name == null ? nickname : "%s(%s)".formatted(nickname, name);
  }
}
