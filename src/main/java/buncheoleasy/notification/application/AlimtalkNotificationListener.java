package buncheoleasy.notification.application;

import static buncheoleasy.notification.infrastructure.AlimtalkExecutorConfig.ALIMTALK_EXECUTOR;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
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
import buncheoleasy.user.domain.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

  // 멤버명 "나열" 예산. 최종 문자열 상한이 아니다 — 접힐 때 뒤에 " 외 N" 이 더 붙으므로 결과는 이 값을 조금 넘는다.
  // 알림톡 문안의 "▶ 참여 멤버:" 한 줄이 감당할 만한 길이로 잡았다.
  private static final int MEMBER_NAME_LIST_BUDGET = 60;
  private static final String MEMBER_NAME_DELIMITER = ", ";

  private final NotificationAssembler assembler;
  private final AlimtalkSender sender;
  private final NotificationInboxRecorder inboxRecorder;

  // 정원 충족 알림을 보낸 분철 id. 취소→재신청 루프의 무제한 재발송(알림톡 건당 과금)을 막는 베스트 에포트 가드로,
  // 재시작·배포 전환 시 초기화돼 그 후 재충족되면 1건 더 갈 수 있다 — 정확한 1회 보장이 아니라 스팸 차단이 목적.
  private final Set<Long> fullNotifiedBuncheolIds = ConcurrentHashMap.newKeySet();

  /**
   * (참여자) 개최자가 입금을 확인함. 참여가 확정됐다. LEGACY 는 다음 관문이 최소 인원 충족이지만 C2C 는 인원이 이미 채워진 뒤라 함께 참여한 사람들의
   * 입금이 남은 조건이어서, 다음 안내 문구가 갈린다.
   */
  @Async(ALIMTALK_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentConfirmed(final PaymentConfirmedEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    AlimtalkTemplate template =
        view.buncheol().isC2c()
            ? AlimtalkTemplate.C2C_PAYMENT_CONFIRMED
            : AlimtalkTemplate.PAYMENT_CONFIRMED;
    Map<String, String> variables =
        Map.of(
            "닉네임", view.participant().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "멤버명", view.memberName(),
            "입금금액", AlimtalkFormats.amount(view.paymentAmount()));
    recordSafely(view.participant().getId(), template, variables);
    sender.send(template, view.participant().getPhoneNumber().value(), variables);
  }

  /**
   * (참여자) 입금 기한이 지나 입금 만료 스케줄러가 참여를 자동 취소함. LEGACY 는 플랫폼 환불 안내, C2C 는 돈이 개최자 계좌로 가는 직거래라 문의
   * 유도 문안으로 갈린다 (docs/46 §6.2).
   */
  @Async(ALIMTALK_EXECUTOR)
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

  /**
   * (참여자) 참여한 분철의 진행이 확정됨 — LEGACY 는 최소 인원 충족, C2C 는 전원 입금확인이 조건이라 문안이 갈린다. C2C 1인 다슬롯에서 같은
   * 알림이 슬롯 수만큼 가지 않도록 유저 단위로 묶어 1건씩 보낸다 (성사 안내와 같은 합산 규칙).
   */
  @Async(ALIMTALK_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolConfirmed(final BuncheolConfirmedEvent event) {
    // 전이분 스냅샷을 상태 재확인 없이 그대로 쓴다 — 분철 CONFIRMED 는 종착 상태라(어느 CAS 도 CONFIRMED 에서 출발하지
    // 않는다) 커밋~실행 사이에 확정 참여가 이탈할 전이가 없다. PAYMENT_COLLECTING 이 열려 있는 성사 안내 쪽과 다른 이유다.
    sendEachSafely(
        groupByParticipant(loadViewsSafely(event.participationIds())), this::sendConfirmedNotice);
  }

  // 다슬롯 참여자에게는 멤버명을 모두 나열해 1건만 보낸다. 금액이 없는 문안이라 합산 대상은 멤버명뿐이다.
  private void sendConfirmedNotice(final List<ParticipationView> group) {
    ParticipationView first = group.get(0);
    AlimtalkTemplate template =
        first.buncheol().isC2c()
            ? AlimtalkTemplate.C2C_BUNCHEOL_CONFIRMED
            : AlimtalkTemplate.BUNCHEOL_CONFIRMED;
    Map<String, String> variables =
        Map.of(
            "닉네임", first.participant().getNickname().value(),
            "분철명", first.buncheol().getTitle(),
            "멤버명", mergedMemberName(group));
    recordSafely(first.participant().getId(), template, variables);
    sender.send(template, first.participant().getPhoneNumber().value(), variables);
  }

  /** (참여자) 참여한 분철이 취소됨(개최자 취소·미달·C2C 미성사). 문안은 {@link #cancelTemplate} 기준으로 갈린다. */
  @Async(ALIMTALK_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    ParticipationView view = assembler.loadByParticipation(event.participationId());
    AlimtalkTemplate template = cancelTemplate(view, event.reason());
    Map<String, String> variables =
        template == AlimtalkTemplate.C2C_BUNCHEOL_NOT_FINALIZED
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

  /**
   * 취소 문안 선택. 판정 기준은 입금 이력이 아니라 취소 사유다 — C2C 성사 후(PAYMENT_COLLECTING) 개최자 취소도 입금 전 참여자에겐 이력이 없는데,
   * 이력만 보고 미성사 문안을 보내면 "성사되지 않아 취소" 라는 거짓 안내에 사유까지 누락된다. 미성사({@code NOT_FINALIZED})는 무입금 신청
   * 단계에서만 나오는 C2C 전용 사유라 환불 안내가 필요 없고(불변식이 다른 패키지에 있어 flowType 도 함께 본다), 나머지는 환불 주체가 갈린다 — LEGACY 는 플랫폼, C2C 는 대금이 개최자 계좌로 직접 간
   * 직거래라 개최자다.
   */
  private AlimtalkTemplate cancelTemplate(
      final ParticipationView view, final BuncheolCancelReason reason) {
    if (reason == BuncheolCancelReason.NOT_FINALIZED && view.buncheol().isC2c()) {
      return AlimtalkTemplate.C2C_BUNCHEOL_NOT_FINALIZED;
    }
    return view.buncheol().isC2c()
        ? AlimtalkTemplate.C2C_BUNCHEOL_CANCELLED
        : AlimtalkTemplate.BUNCHEOL_CANCELLED;
  }

  /** (참여자) C2C 분철 성사 확정 — 입금 안내를 유저 단위로 합산해 1건씩 발송한다 (docs/46 §4.7-A3). */
  @Async(ALIMTALK_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCollectingStarted(final BuncheolCollectingStartedEvent event) {
    // 커밋 시점 전이 대상 스냅샷(event.participationIds)만 발송한다 — 실행 시점 상태 재조회는 그 사이 들어온
    // 추가 모집 참여(onParticipationCreated 로 개별 안내를 받음)가 섞여 중복 발송된다. 커밋~실행 사이에
    // 취소·확정된 건은 입금 안내가 무의미하므로 상태 필터로 거른다.
    List<ParticipationView> views =
        loadViewsSafely(event.participationIds()).stream()
            .filter(
                view ->
                    view.participation().getStatus() == ParticipationStatus.AWAITING_PAYMENT
                        || view.participation().getStatus() == ParticipationStatus.PAYMENT_SENT)
            .toList();
    sendEachSafely(groupByParticipant(views), this::sendFinalizedNotice);
  }

  /** (참여자) C2C 추가 모집 즉시입금 진입 — 이미 성사된 분철의 빈 슬롯 참여라 입금 안내를 바로 보낸다 (docs/46 §4.7-E1). */
  @Async(ALIMTALK_EXECUTOR)
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

  /** (개최자) C2C 모집 정원 충족 — 분철 관리에서 진행 확정을 눌러달라고 독촉한다. 분철당 1회만 발송한다(인메모리 가드). */
  @Async(ALIMTALK_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolFull(final BuncheolFullEvent event) {
    if (!fullNotifiedBuncheolIds.add(event.buncheolId())) {
      return;
    }
    BuncheolHostView view = assembler.loadBuncheolHost(event.buncheolId());
    Map<String, String> variables =
        Map.of(
            "닉네임", view.host().getNickname().value(),
            "분철명", view.buncheol().getTitle(),
            "신청인원", String.valueOf(event.applicantCount()),
            // 본문엔 없고 버튼 링크·수신함 경로(분철 관리 화면)에서만 치환되는 변수.
            "분철ID", String.valueOf(view.buncheol().getId()));
    recordSafely(view.host().getId(), AlimtalkTemplate.C2C_BUNCHEOL_FULL, variables);
    String hostPhone = hostPhoneOrNull(view.host(), event.buncheolId());
    if (hostPhone == null) {
      return;
    }
    sender.send(AlimtalkTemplate.C2C_BUNCHEOL_FULL, hostPhone, variables);
  }

  // 개최자는 참여자와 달리 전화번호 등록 게이트(requireProfileCompleted)를 거치지 않았을 수 있다(소셜 가입 직후 미입력).
  // 발송만 거르고 수신함 기록은 남긴다.
  private String hostPhoneOrNull(final User host, final Long buncheolId) {
    if (host.getPhoneNumber() == null) {
      log.error("개최자 전화번호 미등록으로 알림톡 발송 건너뜀 - buncheolId={}", buncheolId);
      return null;
    }
    return host.getPhoneNumber().value();
  }

  /**
   * (개최자) C2C 참여자가 '보냈어요' 를 누름 — 통장을 확인하고 입금 확인(또는 반려)해 달라고 요청한다. 마킹(슬롯) 건당 1건이라 다슬롯 참여자는
   * 슬롯 수만큼 발송될 수 있고, 금액도 슬롯별 금액이다 — 이체 1건과의 대조는 입금자명(환불 계좌 예금주, docs/46 §4.7-A1)으로 한다.
   */
  @Async(ALIMTALK_EXECUTOR)
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
    String hostPhone = hostPhoneOrNull(view.host(), view.buncheol().getId());
    if (hostPhone == null) {
      return;
    }
    sender.send(AlimtalkTemplate.C2C_PAYMENT_SENT, hostPhone, variables);
  }

  /** (참여자) C2C 개최자가 "보냈어요" 를 반려함 — 연장된 새 기한을 담아 입금 재확인을 안내한다 (docs/46 §4.5). */
  @Async(ALIMTALK_EXECUTOR)
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

  // 다슬롯 참여자에게는 멤버명 나열·금액 합산으로 1건만 보낸다. 계좌·기한은 분철의 확정 시점 스냅샷 (docs/46 §4.7-B1).
  private void sendFinalizedNotice(final List<ParticipationView> group) {
    ParticipationView first = group.get(0);
    long totalAmount = group.stream().mapToLong(ParticipationView::paymentAmount).sum();
    sendC2cPaymentGuide(
        first,
        AlimtalkTemplate.C2C_BUNCHEOL_FINALIZED,
        mergedMemberName(group),
        totalAmount,
        first.buncheol().getPaymentDueAt());
  }

  // 다건 이벤트의 뷰 조립은 건별로 격리한다 — 참여·분철·멤버슬롯·유저 중 하나만 결손이어도(예: group_members 누락)
  // 조립이 예외를 던지는데, 일괄 조회로 묶으면 그 한 건이 같은 분철 수신자 전원의 알림을 없앤다.
  private List<ParticipationView> loadViewsSafely(final List<Long> participationIds) {
    List<ParticipationView> views = new ArrayList<>();
    for (final Long participationId : participationIds) {
      try {
        views.add(assembler.loadByParticipation(participationId));
      } catch (final RuntimeException e) {
        log.error("알림 대상 조립 실패로 해당 참여만 발송 건너뜀 - participationId={}", participationId, e);
      }
    }
    // 건별 로그만으로는 "1건 결손" 과 "DB 장애로 전건 실패" 가 같은 모양이라, 결손이 있을 때만 비율을 한 줄로 남긴다.
    if (views.size() != participationIds.size()) {
      log.error("알림 대상 조립 실패 {}/{}건", participationIds.size() - views.size(), participationIds.size());
    }
    return views;
  }

  // 수신자별 발송을 격리한다 — 알리고 통신 오류·실패 응답은 AlimtalkSendException(RuntimeException) 으로 올라오는데,
  // 다건 이벤트에서 그대로 두면 앞사람 한 명의 발송 실패가 뒷사람 전원의 알림톡과 수신함 기록까지 없앤다.
  private void sendEachSafely(
      final Collection<List<ParticipationView>> groups,
      final Consumer<List<ParticipationView>> sendOne) {
    for (final List<ParticipationView> group : groups) {
      try {
        sendOne.accept(group);
      } catch (final RuntimeException e) {
        log.error(
            "알림 발송 실패로 해당 수신자만 건너뜀 - participantId={}",
            group.get(0).participant().getId(),
            e);
      }
    }
  }

  // 유저 단위 합산 발송용 그룹핑. 같은 이벤트에서 두 유저의 발송 순서가 실행마다 뒤집히지 않도록 입력 순서를 보존한다.
  private Collection<List<ParticipationView>> groupByParticipant(
      final List<ParticipationView> views) {
    return views.stream()
        .collect(
            Collectors.groupingBy(
                view -> view.participant().getId(), LinkedHashMap::new, Collectors.toList()))
        .values();
  }

  /**
   * 다슬롯 참여자의 멤버명 표기 — <b>전부 나열한다</b>.
   *
   * <p>예전에는 "첫 멤버 외 N" 으로 줄였는데, 금액은 합산해 1건으로 보내면서 멤버명만 접으면 참여자가 그 금액이 무엇에 대한 것인지 대조할 수 없다. 특히
   * 다슬롯은 배송비가 묶음 첫 슬롯에만 붙어 슬롯별 금액이 다르므로(docs/53 Q-22), 무엇을 신청했는지가 금액을 이해하는 유일한 단서다.
   *
   * <p>다만 한 사람이 가져갈 수 있는 슬롯 수에 상한이 없어, 나열이 {@value #MEMBER_NAME_LIST_BUDGET} 자를 넘으면
   * 담기는 데까지만 나열하고 나머지는 "외 N" 으로 접는다. 이 상한의 근거는 <b>합산 문안 한 줄의 가독성</b>이지 템플릿 전체 길이 제한이
   * 아니다 — 후자라면 단일 슬롯 경로({@code view.memberName()} 을 그대로 넘기는 곳들)까지 함께 막아야 하고, 그건
   * {@code AlimtalkSender}/{@code AlimtalkTemplate#render} 레이어의 몫이다.
   */
  private String mergedMemberName(final List<ParticipationView> group) {
    final List<String> names = group.stream().map(ParticipationView::memberName).toList();
    // 한 명이면 예산과 무관하게 원본을 그대로 쓴다. 접기 분기에 들여보내면 담을 다음 이름이 없어 " 외 0" 이 붙는다.
    // 합산할 게 없으니 자를 이유도 없다 — 이 예산은 여러 이름을 한 줄에 늘어놓을 때의 가독성 장치다.
    if (names.size() == 1) {
      return names.getFirst();
    }

    final String joined = String.join(MEMBER_NAME_DELIMITER, names);
    if (joined.length() <= MEMBER_NAME_LIST_BUDGET) {
      return joined;
    }

    // 상한을 넘으면 담기는 만큼만 나열한다. 첫 한 명은 길이와 무관하게 항상 이름으로 남겨 "외 N" 만 남는 문안을 막는다.
    final StringBuilder listed = new StringBuilder(names.getFirst());
    int listedCount = 1;
    while (listedCount < names.size()
        && listed.length() + MEMBER_NAME_DELIMITER.length() + names.get(listedCount).length()
            <= MEMBER_NAME_LIST_BUDGET) {
      listed.append(MEMBER_NAME_DELIMITER).append(names.get(listedCount));
      listedCount++;
    }
    return listed + " 외 " + (names.size() - listedCount);
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
  @Async(ALIMTALK_EXECUTOR)
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
  @Async(ALIMTALK_EXECUTOR)
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
  @Async(ALIMTALK_EXECUTOR)
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
  @Async(ALIMTALK_EXECUTOR)
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
