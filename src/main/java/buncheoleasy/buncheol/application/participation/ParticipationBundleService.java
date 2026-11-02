package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmer;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.application.PaymentStarter;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.participation.BundleReleasability;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 묶음 단위 개최자 조작. 묶음은 현실의 돈 단위(이체 1회·배송비 1회·택배 1개)이므로 조작도 그 단위로 한다. */
@Service
@RequiredArgsConstructor
public class ParticipationBundleService {

  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ParticipationRepository participationRepository;
  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolConfirmer buncheolConfirmer;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 이 트랜잭션의 CAS 가 쓴 시각인지 판정한다.
   *
   * <p>🔴 <b>{@code equals} 로 비교하면 안 된다.</b> {@code payment_sent_at} 은 초 단위 {@code DATETIME}(precision 0)
   * 이라 나노초를 가진 {@code Instant} 와 <b>영원히 같지 않다</b>. H2 는 정밀도를 보존해 테스트로는 드러나지 않는다.
   */
  private static boolean writtenAt(final Instant stored, final Instant now) {
    return stored != null && !stored.isBefore(now.truncatedTo(ChronoUnit.SECONDS));
  }

  /**
   * 참여자가 묶음을 한 번에 「보냈어요」로 표시한다 (docs/71 §1-2).
   *
   * <p>묶음은 <b>이체 1회</b>의 단위다. 슬롯마다 누르게 하면 ① 참여자가 한 번 보낸 돈을 여러 번 신고하게 되고
   * ② 중간에 멈추면 같은 묶음의 슬롯 상태가 갈린다 — 입금확인이 "묶음 안 슬롯은 상태가 갈리지 않는다" 는 전제
   * 위에 서 있으므로 그 전제를 여기서 지켜야 한다.
   *
   * <p><b>기한 검사가 없다.</b> 기한이 지난 뒤에도 마킹은 가능해야 한다 — 늦게 보낸 사람도 보냈다는 사실을
   * 남길 수 있어야 개최자가 확인하고, 진행 확정 때 「보냈어요」를 누른 사람이 강조된다.
   *
   * <p>멱등하다. 이미 전부 마킹됐으면 이벤트 없이 조용히 끝난다 — 더블탭으로 개최자 알림이 중복되면 안 된다.
   */
  @Transactional
  public List<Long> markPaymentSent(final Long participantId, final Long bundleId) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle =
        participationBundleDomainService
            .findById(bundleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNDLE_NOT_FOUND));
    // 🔴 묶음 id 는 AUTO_INCREMENT 라 추측 가능하다 — 소유자 검증이 필수다.
    if (!participantId.equals(bundle.getParticipantId())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
    // 분철 행이 첫 잠금이다(BuncheolDomainService#getBuncheolForUpdate). 묶음 조회는 분철 조회가 아니라 먼저 해도 된다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(bundle.getBuncheolId());
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    int marked = participationRepository.markBundlePaymentSent(bundleId, now);

    // 🔴 「보냈어요」 시각의 정본은 묶음이다 — 이체가 한 번이므로 신고도 한 번이다.
    // 자리에도 같은 시각이 찍히지만(상태 전이 CAS 와 한 몸) 그건 사본이고, 읽기가 전부 이쪽으로
    // 옮겨진 뒤 제거한다. 자리 마킹이 실제로 일어났을 때만 기록해 더블탭이 시각을 밀지 않게 한다.
    if (marked > 0) {
      participationBundleDomainService.markPaymentSent(bundleId, now);
    }

    // 🔴 <b>잠금 조회로 다시 읽는다.</b> 평범한 SELECT 는 REPEATABLE READ 라 트랜잭션 첫 읽기 시점의 스냅샷을
    // 본다 — 동시 더블탭에서 A 가 먼저 커밋하면 B 의 UPDATE 는 current read 로 0행이 되는데, 이어지는 일반
    // 조회는 <b>아직 마킹 전으로 보여</b> "마킹할 게 없다" 며 409 를 낸다. 이 기능이 막으려던 더블탭이 정확히
    // 그 경합이다.
    List<Participation> slots = participationRepository.findAllByBundleIdForUpdate(bundleId);
    List<Long> sentIds =
        slots.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.PAYMENT_SENT)
            .map(Participation::getId)
            .toList();

    if (marked == 0) {
      // 멱등: 이미 마킹된 슬롯이 있으면 성공으로 끝낸다(더블탭). 하나도 없으면 상태 위반이다 — 개최자가
      // 이미 전부 입금확인한 뒤 누른 경우도 여기 해당한다.
      if (sentIds.isEmpty()) {
        throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
      }
      return sentIds;
    }

    // 응답은 <b>이번 호출이</b> 마킹한 슬롯이다 — 화면이 사후 대조하는 대상이 그것이다.
    List<Long> markedIds =
        slots.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.PAYMENT_SENT)
            .filter(p -> writtenAt(p.getPaymentSentAt(), now))
            .map(Participation::getId)
            .toList();
    // 알림은 <b>묶음 전체</b>의 마킹분으로 만든다 — 개최자는 이 금액으로 이체 1건을 대조한다(BundlePaymentSentEvent).
    eventPublisher.publishEvent(new BundlePaymentSentEvent(bundleId, markedIds, sentIds));
    return markedIds;
  }

  /**
   * 개최자가 진행 확정 뒤 추가 신청 묶음을 <b>한 번에</b> 수락한다 (APPLIED → AWAITING_PAYMENT). 묶음 기한은 지금 + 24시간이고,
   * 참여자에게 계좌·금액·기한 알림톡이 간다. 일부만 수락하는 것은 없다 — 묶음은 이체 1회·택배 1개다.
   *
   * <p>{@code expectedSlotIds} 는 관리 응답에서 {@code acceptTarget} 이 참인 슬롯이다. 잠금 아래 서버가 본 수락 전 슬롯 집합과
   * 다르면 409(BCH-115)다(입금 확인과 같은 대조). 진행 확정 분철이 아니거나 수락 전 슬롯이 없으면 BCH-132.
   *
   * <p>분철 행이 첫 잠금이다. 잠금 전 일반 조회는 묶음 하나뿐이고 판단은 묶음 참여 잠금 조회와 CAS 로만 한다 — 기다리는 사이 참여자가
   * 취소했으면 잠금 조회가 그것을 본다.
   */
  @Transactional
  public void accept(final Long hostId, final Long bundleId, final List<Long> expectedSlotIds) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle = getBundle(bundleId);
    lockConfirmedForExtraDecision(hostId, bundle);
    List<Long> appliedIds = requireAppliedSlots(bundleId, expectedSlotIds);

    Instant dueAt = now.plus(PaymentStarter.PAYMENT_TIME_LIMIT);
    if (participationRepository.acceptBundleIfApplied(bundleId, dueAt, now) != appliedIds.size()) {
      // 잠금을 쥔 채 판정했으므로 여기 오면 내부 불변식 위반이다 — 롤백해 일부만 넘어간 묶음을 남기지 않는다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participationBundleDomainService.assignDueAt(bundleId, dueAt, now);
    eventPublisher.publishEvent(new ExtraAcceptedEvent(bundleId, appliedIds));
  }

  /**
   * 개최자가 진행 확정 뒤 추가 신청 묶음을 <b>한 번에</b> 거절한다 (APPLIED → CANCELLED, {@code HOST_REJECTED}). 묶음이 닫히고 그
   * 자리는 다시 빈다. 대조와 오류는 {@link #accept} 와 같다.
   */
  @Transactional
  public void reject(final Long hostId, final Long bundleId, final List<Long> expectedSlotIds) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle = getBundle(bundleId);
    lockConfirmedForExtraDecision(hostId, bundle);
    List<Long> appliedIds = requireAppliedSlots(bundleId, expectedSlotIds);

    if (participationRepository.rejectBundleIfApplied(bundleId, now) != appliedIds.size()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participationBundleDomainService.closeIfEmpty(bundleId, now);
    eventPublisher.publishEvent(new ExtraRejectedEvent(bundleId, appliedIds));
  }

  /**
   * 개최자가 진행 확정 뒤 수락한 추가 묶음을 「제외」한다 — 입금 기한(수락 + 24시간)이 지나도록 입금 확인이 안 된 묶음의 입금 대기·「보냈어요」
   * 슬롯을 {@code HOST_RELEASED} 로 취소하고 묶음을 닫는다. 그 자리는 다시 비고, 참여자에게 참여 취소 안내가 간다.
   *
   * <p>판정({@link BundleReleasability})으로 사유를 가려 오류 코드로 바꾸고, 실제 차단은 CAS 가 한다. 판정은 관리 응답의 {@code
   * releasability} 와 같다.
   *
   * <p>분철 행이 첫 잠금이다 — 같은 묶음의 입금 확인과 분철 행 앞에서 한 줄로 서서, 어느 쪽이 이기든 하나만 반영된다(확인이 이기면
   * BCH-135, 「제외」가 이기면 확인이 BCH-116).
   *
   * @return 이 호출이 실제로 취소한 슬롯 id
   */
  @Transactional
  public List<Long> release(final Long hostId, final Long bundleId) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle = getBundle(bundleId);
    Buncheol buncheol = lockForHost(hostId, bundle);

    // 🔴 잠금 조회로 판정한다. 확정 슬롯 검사를 CAS 서브쿼리에 넣을 수 없고(MySQL error 1093), 이 잠금이 입금 확인 CAS 를 막는다.
    requireReleasable(
        BundleReleasability.of(
            buncheol.getStatus(),
            bundle,
            participationRepository.findAllByBundleIdForUpdate(bundleId),
            now));

    if (participationRepository.releaseBundleIfDue(bundleId, now) == 0) {
      // 판정을 통과했는데 0행이면 그 사이 상태가 바뀐 것이다 — 화면을 새로 고치게 한다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participationBundleDomainService.closeIfEmpty(bundleId, now);

    // CAS 뒤에 이 트랜잭션이 실제로 취소한 슬롯을 고른다(사유와 같은 now 로 쓴 취소 시각). CAS 전 목록으로 만들면 그 사이 바뀐 슬롯이
    // 섞여 알림이 엉뚱한 슬롯을 "기한이 지나 취소됐어요" 로 안내한다.
    List<Long> releasedIds =
        participationRepository.findAllByBundleIds(List.of(bundleId)).stream()
            .filter(p -> p.getCancelReason() == ParticipationCancelReason.HOST_RELEASED)
            .filter(p -> writtenAt(p.getCancelledAt(), now))
            .map(Participation::getId)
            .toList();
    eventPublisher.publishEvent(new BundleReleasedEvent(bundleId, releasedIds));
    return releasedIds;
  }

  /** 판정 → 오류 코드. switch <b>식</b>이라 사유가 늘면 컴파일 오류로 잡힌다(fail-open 방지). */
  private static void requireReleasable(final BundleReleasability releasability) {
    ErrorCode errorCode =
        switch (releasability) {
          case RELEASABLE -> null;
          case BUNCHEOL_NOT_CONFIRMED, NOT_ACCEPTED -> ErrorCode.BUNDLE_RELEASE_NOT_ALLOWED;
          case BEFORE_DUE -> ErrorCode.BUNDLE_RELEASE_BEFORE_DUE;
          case HAS_CONFIRMED -> ErrorCode.BUNDLE_RELEASE_HAS_CONFIRMED;
          case ALREADY_CLOSED -> ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID;
        };
    if (errorCode != null) {
      throw new BusinessException(errorCode);
    }
  }

  private ParticipationBundle getBundle(final Long bundleId) {
    return participationBundleDomainService
        .findById(bundleId)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNDLE_NOT_FOUND));
  }

  // 분철 행이 첫 잠금이다(BuncheolDomainService#getBuncheolForUpdate). 운영진 분철은 잠근 뒤 BCH-084 — 묶음 입금 확인과 같은 꼴이다.
  private Buncheol lockForHost(final Long hostId, final ParticipationBundle bundle) {
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(bundle.getBuncheolId());
    buncheol.validateOwner(hostId);
    if (buncheol.isStaff()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
    return buncheol;
  }

  // 수락·거절은 진행 확정 분철에서만 있다. 모집 중 신청(APPLIED)은 입금 단계 시작이 넘긴다.
  private void lockConfirmedForExtraDecision(final Long hostId, final ParticipationBundle bundle) {
    if (lockForHost(hostId, bundle).getStatus() != BuncheolStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.BUNDLE_ACCEPT_NOT_ALLOWED);
    }
  }

  // 잠금 조회로 묶음의 수락 전 슬롯을 고르고 화면이 본 목록과 집합으로 대조한다.
  private List<Long> requireAppliedSlots(final Long bundleId, final List<Long> expectedSlotIds) {
    List<Long> appliedIds =
        participationRepository.findAllByBundleIdForUpdate(bundleId).stream()
            .filter(p -> p.getStatus() == ParticipationStatus.APPLIED)
            .map(Participation::getId)
            .sorted()
            .toList();
    if (appliedIds.isEmpty()) {
      throw new BusinessException(ErrorCode.BUNDLE_ACCEPT_NOT_ALLOWED);
    }
    if (!Set.copyOf(appliedIds).equals(Set.copyOf(expectedSlotIds))) {
      throw new BusinessException(ErrorCode.BUNDLE_SLOTS_CHANGED);
    }
    return appliedIds;
  }

  /**
   * 개최자가 묶음의 입금을 <b>한 번에</b> 확인한다 (docs/70 결정 6 — all-or-nothing).
   *
   * <p>묶음은 <b>이체 1회</b>의 단위다. 슬롯마다 확인하면 ① 개최자가 한 건의 입금을 여러 번 처리하게 되고
   * ② 중간에 멈추면 같은 묶음의 슬롯 상태가 갈린다. <b>부분 확인은 애초에 성립하지 않는다</b> — 확인 API 에
   * 금액이 없어 시스템은 실입금액을 모르고, 개최자가 판단하는 것은 "이 이체가 들어왔는가" 하나뿐이다
   * (docs/70 §10-10).
   *
   * <p>🔴 <b>{@code expectedSlotIds} 를 받는다.</b> 화면은 개최 관리 응답의 {@code confirmTarget} 이 참인
   * 슬롯만 실어야 한다 — 판정을 서버가 주므로 화면이 상태를 해석하지 않는다. 서버의 실제 집합과 다르면
   * 409 로 막고 새로고침을 유도한다 — 개최자가 <b>보지 못한 슬롯까지 확정</b>하면 안 된다.
   *
   * <p>기한 경과를 검사하지 않는다 — 개최자 확인이 늦어도 유효해야 한다 (docs/46 §3-6).
   *
   * <p>이 묶음으로 미확인이 0 이 되면 같은 트랜잭션에서 분철을 진행 확정한다({@link BuncheolConfirmer}) — 확인된 묶음마다 배송이
   * 생기고 진행확정 알림이 간다. 진행 확정이 실패하면(배송지 없음 등) 이 입금 확인도 함께 되돌린다. 확인만 남기면 "모두 확인됐는데 확정이
   * 안 된" 분철이 생긴다.
   *
   * <p>진행 확정 뒤 추가 묶음이면(잠근 분철이 CONFIRMED) 같은 트랜잭션에서 <b>그 묶음의 배송을 바로 만들고</b> 이 묶음 참여자에게만
   * 진행 확정 안내를 보낸다 — 운송장 칸이 열린다. 입금 확인 안내({@link BundlePaymentConfirmedEvent})는 "진행 확정되면 다시 알려
   * 드릴게요" 라는 약속이라 이미 진행 확정된 분철에서는 보내지 않는다. 응답의 {@code buncheolConfirmed} 는 false 다.
   *
   * <p>🔴 READ COMMITTED 다({@link BuncheolConfirmer} 계약). 분철 행 잠금 앞에서 묶음을 일반 조회하므로, REPEATABLE READ 면 그
   * 스냅샷이 남아 잠금을 기다리는 사이 다른 탭에서 확인된 묶음의 참여자가 진행확정 알림 대상(일반 조회)에서 빠진다. 판단(대조, 다시 세기,
   * CAS)은 모두 잠금 읽기라 격리에 흔들리지 않고, 입금 수집 중에는 새 참여 INSERT 가 없어 간격 잠금이 빠져도 된다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BundleConfirmResult confirmPayment(
      final Long hostId, final Long bundleId, final List<Long> expectedSlotIds) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle =
        participationBundleDomainService
            .findById(bundleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNDLE_NOT_FOUND));
    // 분철 행이 첫 잠금이다(BuncheolDomainService#getBuncheolForUpdate). 아래 참여 잠금은 그 뒤에 잡는다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(bundle.getBuncheolId());
    buncheol.validateOwner(hostId);
    // LEGACY 는 1인 1활성슬롯이라 묶음 = 참여다. 열어 두면 페이액션·배송 경로까지 묶음 계약을 검증해야 한다.
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    // 잠금 조회로 앞세운다 — all-or-nothing 판정을 CAS 서브쿼리로 넣을 수 없고(MySQL error 1093),
    // 락이 있어야 판정과 UPDATE 사이에 참여자의 자발 취소·마킹이 끼어들지 못한다.
    List<Participation> payable =
        participationRepository.findAllByBundleIdForUpdate(bundleId).stream()
            .filter(p -> ParticipationStatus.payableStatuses().contains(p.getStatus()))
            .toList();
    List<Long> payableIds = payable.stream().map(Participation::getId).sorted().toList();
    if (payableIds.isEmpty()) {
      throw new BusinessException(ErrorCode.BUNDLE_CONFIRM_NOT_ALLOWED);
    }
    // 순서 무관하게 집합으로 대조한다 — 화면이 정렬을 보장하지 않는다.
    if (!Set.copyOf(payableIds).equals(Set.copyOf(expectedSlotIds))) {
      throw new BusinessException(ErrorCode.BUNDLE_SLOTS_CHANGED);
    }

    int confirmed = participationRepository.confirmBundleIfPayable(bundleId, now);
    if (confirmed != payableIds.size()) {
      // 락을 쥔 채 판정했으므로 여기 오면 내부 불변식 위반이다 — 롤백해 부분 확정을 남기지 않는다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }

    if (buncheol.getStatus() == BuncheolStatus.CONFIRMED) {
      // 묶음당 배송 1개 — 두 번째 슬롯부터는 DeliverySnapshotCreator 가 건너뛴다. READ COMMITTED 라 배송 유무 조회가 최신이다.
      payable.forEach(deliverySnapshotCreator::create);
      eventPublisher.publishEvent(new BuncheolConfirmedEvent(bundle.getBuncheolId(), payableIds));
      return new BundleConfirmResult(payableIds, false);
    }

    eventPublisher.publishEvent(new BundlePaymentConfirmedEvent(bundleId, payableIds));

    // 분철 행을 쥔 채 다시 센다. 위 대조는 이 묶음뿐이고, 다른 묶음은 분철 행을 기다리는 사이 다른 탭의 입금 확인·「보냈어요」가
    // 바꿨을 수 있다. 잠금 읽기는 최신 커밋을 보고, 그 뒤 같은 분철의 회원 개최 쓰기는 분철 행 앞에서 기다린다.
    List<Participation> active =
        participationDomainService.findActiveByBuncheolIdForUpdate(bundle.getBuncheolId());
    ConfirmCheck check =
        ConfirmCheck.of(buncheol.getStatus(), active, buncheol.getPaymentDueAt(), now);
    if (check != ConfirmCheck.ALL_PAID) {
      return new BundleConfirmResult(payableIds, false);
    }
    buncheolConfirmer.confirm(bundle.getBuncheolId(), active, now);
    return new BundleConfirmResult(payableIds, true);
  }
}
