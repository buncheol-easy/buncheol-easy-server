package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRequestedEvent;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.feedback.application.FeedbackSubmittedEvent;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
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

  /** 알림 미리보기용 대체 텍스트에 실을 사용자 입력 길이 상한. 본문 전체는 blocks 에 그대로 실린다. */
  private static final int FALLBACK_TEXT_MAX_LENGTH = 100;

  private final NotificationAssembler assembler;
  private final SlackWebhookClient slackWebhookClient;
  // 분철 상세 링크 base (프론트 웹앱 origin).
  private final String frontendBaseUrl;
  // '어드민에서 처리' 링크 base (어드민 웹 origin).
  private final String adminBaseUrl;

  public SlackNotificationListener(
      final NotificationAssembler assembler,
      final SlackWebhookClient slackWebhookClient,
      @Value("${app.frontend.base-url}") final String frontendBaseUrl,
      @Value("${app.admin.base-url}") final String adminBaseUrl) {
    this.assembler = assembler;
    this.slackWebhookClient = slackWebhookClient;
    this.frontendBaseUrl = frontendBaseUrl;
    this.adminBaseUrl = adminBaseUrl;
  }

  /**
   * (운영자) 신규 참여 접수. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)라 참여 한 건이 메시지 한 건이다. 환급 알림과 같은 Block Kit
   * 구조(헤더 + bold 필드 + 링크)로 조립한다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onParticipationCreated(final ParticipationCreatedEvent event) {
    // 웹훅 미설정 환경(로컬/CI)은 발송하지 않으므로 조립 조회부터 건너뛴다.
    if (!slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)) {
      return;
    }
    // 회원 개최(C2C)는 운영진이 거래 당사자가 아니다 — 입금 확인도 개최자가 하므로 이 알림이 안내하는
    // "입금 기한 내 확인"과 "어드민에서 처리"가 둘 다 운영진의 일이 아니다. 게다가 C2C 참여는 성사 확정
    // 전까지 입금 기한이 없어(dueAt=null) 본문 조립이 NPE 로 죽는다 — 모집중 신청마다 ERROR 가 쌓여
    // 진짜 장애를 가린다. 조립 조회 전에 끊는다(웹훅 미설정 분기와 같은 이유).
    if (event.flowType() == FlowType.C2C) {
      return;
    }
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    // 0원 참여는 안내할 입금액도 기한도 없어 같은 블록으로 조립할 수 없다. 판정은 반드시 금액(isFree)으로 한다 —
    // 계좌 유무로 바꾸면 안 된다. 참여 계좌 강제(PR #151) 이후로는 0원 참여도 계좌를 갖는다(docs/80 결정 1).
    if (view.participation().isFree()) {
      sendFreeParticipation(view);
      return;
    }
    // 정본은 묶음이다 (P2-c). 배포선 창의 미연결 참여는 비어 있을 수 있어 대체 문자열로 낸다 —
    // ⚠️ 발송 자체를 스킵하면 안 된다. 이 알림이 LEGACY 수동 입금확인의 출발점이고 페이액션 실패 시 백업이다.
    RefundAccount refundAccount = view.bundle() == null ? null : view.bundle().getRefundAccount();

    String fallbackText =
        "🔔 [신규 참여] %s (분철 #%d) - %s"
            .formatted(
                view.buncheol().getTitle(), view.buncheol().getId(), formatParticipant(view));
    List<Map<String, Object>> blocks =
        List.of(
            Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "🔔 새로운 참여가 들어왔어요!")),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    markdown("*참여자*\n%s".formatted(formatParticipant(view))),
                    markdown(
                        "*분철*\n%s (#%d)"
                            .formatted(view.buncheol().getTitle(), view.buncheol().getId())),
                    markdown("*멤버*\n%s".formatted(view.memberName())),
                    markdown(
                        "*입금 예정 금액*\n%s원"
                            .formatted(AlimtalkFormats.amount(view.paymentAmount()))),
                    markdown(refundAccountText(refundAccount)),
                    markdown(
                        "*입금 기한*\n%s (기한 내 입금확인 필요)"
                            .formatted(DUE_AT_FORMAT.format(view.participation().getDueAt()))))),
            Map.of(
                "type",
                "section",
                "text",
                markdown(
                    "<%s/products/%d|분철 상세 보기> · <%s|어드민에서 처리>"
                        .formatted(
                            frontendBaseUrl, view.buncheol().getId(), adminBaseUrl))));
    slackWebhookClient.send(SlackChannel.NEW_PARTICIPATION, fallbackText, blocks);
  }

  /**
   * (운영자) 0원 참여 확정 알림. 입금 확인이 필요 없어 할 일은 없지만, 배정 슬롯이 실제로 쓰였는지가 캠페인 운영의
   * 신호라 별도로 알린다. 코드 사용 여부는 이 이벤트가 알지 못하므로 문구를 코드에 한정하지 않는다.
   */
  private void sendFreeParticipation(final ParticipationView view) {
    String fallbackText =
        "🎟️ [무료 참여 확정] %s (분철 #%d) - %s"
            .formatted(
                view.buncheol().getTitle(), view.buncheol().getId(), formatParticipant(view));
    List<Map<String, Object>> blocks =
        List.of(
            Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "🎟️ 무료 참여가 확정됐어요!")),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    markdown("*참여자*\n%s".formatted(formatParticipant(view))),
                    markdown(
                        "*분철*\n%s (#%d)"
                            .formatted(view.buncheol().getTitle(), view.buncheol().getId())),
                    markdown("*멤버*\n%s".formatted(view.memberName())),
                    markdown("*결제*\n0원 (입금 확인 불필요)"))),
            Map.of(
                "type",
                "section",
                "text",
                markdown(
                    "<%s/products/%d|분철 상세 보기>"
                        .formatted(frontendBaseUrl, view.buncheol().getId()))));
    slackWebhookClient.send(SlackChannel.NEW_PARTICIPATION, fallbackText, blocks);
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
    RefundAccount refundAccount = view.bundle() == null ? null : view.bundle().getRefundAccount();
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
                "text",
                    Map.of("type", "plain_text", "text", "💸 새로운 배송비 환급 요청이 들어왔어요!")),
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
                    markdown(refundAccountText(refundAccount)))),
            Map.of(
                "type",
                "section",
                "text",
                markdown(
                    "<%s|후기 트윗 보기> · <%s|어드민에서 처리>"
                        .formatted(tweetUrl, adminBaseUrl))));
    slackWebhookClient.send(SlackChannel.SHIPPING_FEE_PAYBACK, fallbackText, blocks);
  }

  /**
   * (운영자) 사용자 의견 접수. 의견은 저장하지 않고 슬랙으로만 흘려보내므로 DB 조회 없이 이벤트에 실린 값만으로 조립한다.
   *
   * <p>다른 핸들러와 달리 {@code @TransactionalEventListener} 가 아니라 {@code @EventListener} 다 — 의견 접수는 DB
   * 트랜잭션 안에서 발행되지 않아 AFTER_COMMIT 이 영영 오지 않는다.
   */
  @Async
  @EventListener
  public void onFeedbackSubmitted(final FeedbackSubmittedEvent event) {
    if (!slackWebhookClient.isEnabled(SlackChannel.FEEDBACK)) {
      return;
    }
    String author =
        event.userId() == null
            ? "비로그인"
            : "%s (회원 #%d)".formatted(event.nickname() == null ? "닉네임 미상" : event.nickname(), event.userId());
    String meta =
        event.screenPath() == null || event.screenPath().isBlank()
            ? author
            : "%s · %s".formatted(author, event.screenPath());

    // 최상위 text 는 blocks 와 달리 mrkdwn 으로 해석된다(푸시·데스크톱 알림 미리보기 경로).
    // plain_text 블록으로 막아둔 링크 렌더링이 여기서 새지 않도록 이스케이프하고, 미리보기 길이에 맞춰 자른다.
    String fallbackText =
        "💬 [새 의견] %s - %s".formatted(author, escapeAndAbbreviate(event.content()));
    List<Map<String, Object>> blocks =
        List.of(
            Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "💬 새 의견이 도착했어요")),
            // 사용자 입력은 plain_text 로 싣는다 — mrkdwn 이면 본문의 <http://...|링크> 가 실제 링크로 렌더링된다.
            Map.of("type", "section", "text", plainText(event.content())),
            Map.of("type", "context", "elements", List.of(plainText(meta))));
    slackWebhookClient.send(SlackChannel.FEEDBACK, fallbackText, blocks);
  }

  /**
   * 슬랙 블록의 환불계좌 줄. 🔴 <b>계좌가 없어도 발송은 살린다</b> — 조건 없이 역참조하면 NPE 가 나고, {@code @Async}
   * 라 예외가 워커 스레드 로그로만 끝나 <b>메시지가 통째로 안 나간다</b>.
   *
   * <p>그게 특히 나쁜 이유는 같은 조건에서 {@code DepositOrderListener} 가 페이액션 등록을 스킵하기 때문이다 —
   * 그 스킵의 근거가 "운영자가 이 슬랙 알림을 보고 수동 확인한다" 인데, 그 알림까지 죽으면 <b>자동확인도 안 되고
   * 운영자도 모르는 참여</b>가 된다.
   */
  private static String refundAccountText(final RefundAccount refundAccount) {
    return refundAccount == null
        ? "*환불계좌*\n묶음 미연결 — 계좌 확인 필요"
        : "*환불계좌*\n%s %s (예금주 %s)"
            .formatted(refundAccount.bank(), refundAccount.account(), refundAccount.holder());
  }

  private static Map<String, Object> markdown(final String text) {
    return Map.of("type", "mrkdwn", "text", text);
  }

  private static Map<String, Object> plainText(final String text) {
    return Map.of("type", "plain_text", "text", text);
  }

  /**
   * 슬랙 최상위 {@code text}(mrkdwn 해석) 에 사용자 입력을 실을 때만 쓴다. 슬랙은 {@code &}, {@code <}, {@code >} 를 이스케이프한
   * 텍스트를 요구하며, 이스케이프하지 않으면 본문에 적은 {@code <http://…|링크>} 가 알림 미리보기에서 링크로 렌더링된다.
   */
  private static String escapeAndAbbreviate(final String text) {
    String escaped =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    return escaped.length() <= FALLBACK_TEXT_MAX_LENGTH
        ? escaped
        : escaped.substring(0, FALLBACK_TEXT_MAX_LENGTH) + "…";
  }

  // 입금내역 대조용으로 실명이 있으면 "닉네임(실명)" 으로 병기한다 (기존 회원은 실명이 없을 수 있음).
  private String formatParticipant(final ParticipationView view) {
    String nickname = view.participant().getNickname().value();
    String name = view.participant().getName();
    return name == null ? nickname : "%s(%s)".formatted(nickname, name);
  }
}
