package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.BuncheolConfirmedFinalizer;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 참여 라이프사이클(참여 신청 / 개최자 입금확인 / 참여자 취소) 애플리케이션 서비스. */
@Service
@RequiredArgsConstructor
public class ParticipationService {

  // 참여(개최자 계좌 노출) 시점부터 입금 만료까지의 기본 창. 단, deadline 을 넘지 않도록 클램프한다.
  private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

  // C2C 입금 창(24h — docs/46 §7.1-3). 성사 확정 후 일괄 기한과 추가 모집(즉시입금 진입)의 개별 기한이 같은 창을 쓴다.
  public static final Duration C2C_PAYMENT_WINDOW = Duration.ofHours(24);

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationShippingAddressResolver participationShippingAddressResolver;
  private final UserDomainService userDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 분철 참여(멤버 슬롯 선착순 점유). 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이며, 점유에 성공하면 개최자 계좌가 노출되고 입금 만료
   * 타이머(min(now+30분, deadline))가 시작된다. 참여와 동시에 환불 계좌를 입력받는다.
   *
   * <p>참여는 멤버 금액(amount)과 배송비(shippingFee)를 분리 저장하며, 참여 1건이 곧 이체 1건이므로 배송비는 참여마다 부과된다. 단일 선택
   * 정책으로 분철당 활성 참여는 1건(멤버 1명)만 허용하고, 취소·만료된 참여만 같은 분철에 다시 참여할 수 있다.
   */
  @Transactional
  public ParticipateResult participate(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    final Instant now = Instant.now(clock);

    // 가입 미완료(전화번호 미등록) 유저 차단. 배송 스냅샷이 phoneNumber 를 요구하므로 참여 진입 자체를 막는다.
    userDomainService.requireProfileCompleted(participantId);

    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);

    // C2C 는 신청(무입금)→확정→입금 플로우라 별도 경로로 처리한다. LEGACY 경로는 이하 현행 그대로 (docs/46 §0.1-3).
    if (buncheol.isC2c()) {
      return participateC2c(buncheol, participantId, request, now);
    }

    buncheol.validateRecruiting(now);
    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }

    // 단일 선택 정책: 분철당 참여 1건(멤버 1명). 활성(입금확인중·확정) 참여가 있으면 중복 참여를 막고,
    // 취소·만료된 참여는 재참여를 허용한다. 이 사전 체크의 check-then-insert 갭(동시 이중 요청)은
    // uq_participations_legacy_active_participant 유니크가 최종 차단한다 (C2C 는 다슬롯 허용이라 미적용).
    if (participationDomainService.hasActiveParticipationInBuncheol(buncheolId, participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_ALREADY_JOINED_BUNCHEOL);
    }

    // DTO(@NotNull) 검증과 별개로 서비스에서도 방어 검증한다 — 참여 1건 = 멤버 슬롯 1개(단일 선택 정책).
    // HTTP 경로에서는 @NotNull 이 먼저 걸러 C-001 로 응답하므로 이 분기는 도달하지 않는다.
    // 컨트롤러를 거치지 않는 직접 호출(배치·테스트 등)을 막는 최후 가드로만 동작한다.
    if (request.buncheolMemberId() == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheolId);

    ShippingAddress shippingAddress =
        participationShippingAddressResolver.resolve(
            participantId, buncheol, request.shippingAddressId());
    long shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    Instant dueAt = paymentDueAt(now, buncheol.getDeadline());

    Participation participation =
        Participation.create(
            buncheolId,
            member.getId(),
            participantId,
            shippingAddress.getId(),
            member.getPrice(),
            shippingFee,
            request.toRefundAccount(),
            dueAt);
    // 저장 시점에도 분철이 모집중인지 원자적으로 재확인(없으면 false → 롤백). 멤버 슬롯이 이미 점유됐으면 DuplicateKey →
    // PARTICIPATION_ALREADY_EXISTS 로 롤백.
    if (!participationDomainService.createParticipationIfRecruiting(participation)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 개최자(MVP 운영자)가 입금 기한 내에 확인·입금확인할 수 있도록 커밋 후 운영자 슬랙 채널로 신규 참여를 알린다.
    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.LEGACY));

    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    return new ParticipateResult(
        participation.getId(), participation.getTotalAmount(), dueAt, hostAccount);
  }

  /**
   * C2C 참여 (docs/46 §1.1·§4.7). 모집중(RECRUITING)엔 무입금 신청(APPLIED)으로 슬롯을 선점하고, 성사 확정 후 입금
   * 수집중(PAYMENT_COLLECTING)엔 빈 슬롯에 즉시입금(AWAITING_PAYMENT, 개별 24h)으로 진입한다(추가 모집 — §4.7-E1).
   *
   * <p>다슬롯 일관성(§4.7-A1·A2): 같은 분철에 기존 활성 참여가 있으면 배송지·환불계좌(입금자명)를 강제로 재사용하고 배송비는 첫 참여에만
   * 부과한다 — 개최자 통장 대조(입금자명 1개)와 배송 1묶음을 보장한다. 요청의 배송지·환불계좌 입력은 무시된다(FE 는 프리필+잠금).
   */
  private ParticipateResult participateC2c(
      final Buncheol loaded,
      final Long participantId,
      final ParticipateRequest request,
      final Instant now) {
    // 분철 행 락으로 참여 생성을 직렬화한다 — 같은 유저의 동시 다슬롯 신청에서 "첫 참여 판정"(배송비 1회·배송지/입금자명
    // 스냅샷 재사용)이 둘 다 첫 참여로 오판되는 check-then-insert 레이스를 막는다 (docs/46 §4.7-A1·A2).
    // 성사 확정·취소 CAS 와도 직렬화되어 상태 판정이 안정된다. 분철당 참여 빈도가 낮아 락 경합 부담은 미미하다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(loaded.getId());

    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }
    if (request.buncheolMemberId() == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheol.getId());

    Optional<Participation> existing =
        participationDomainService.findFirstActiveInBuncheol(buncheol.getId(), participantId);
    final Long shippingAddressId;
    final long shippingFee;
    final RefundAccount refundAccount;
    if (existing.isPresent()) {
      shippingAddressId = existing.get().getShippingAddressId();
      shippingFee = 0L;
      refundAccount = existing.get().getRefundAccount();
    } else {
      ShippingAddress shippingAddress =
          participationShippingAddressResolver.resolve(
              participantId, buncheol, request.shippingAddressId());
      shippingAddressId = shippingAddress.getId();
      shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
      refundAccount = request.toRefundAccount();
    }

    return switch (buncheol.getStatus()) {
      case RECRUITING -> applyC2c(buncheol, participantId, member, shippingAddressId, shippingFee, refundAccount, now);
      case PAYMENT_COLLECTING ->
          joinCollectingC2c(buncheol, participantId, member, shippingAddressId, shippingFee, refundAccount, now);
      default -> throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    };
  }

  /** C2C 무입금 신청 (RECRUITING → APPLIED 슬롯 선점). 신청 단계는 계좌·기한 없이 응답한다 (docs/46 §3-1). */
  private ParticipateResult applyC2c(
      final Buncheol buncheol,
      final Long participantId,
      final BuncheolMember member,
      final Long shippingAddressId,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant now) {
    // deadline 경과(확정 유예 대기) 구간의 신규 신청 차단 — docs/46 §4.7-E3. INSERT 의 원자 조건과 이중 방어.
    buncheol.validateRecruiting(now);

    Participation participation =
        Participation.createApplied(
            buncheol.getId(),
            member.getId(),
            participantId,
            shippingAddressId,
            member.getPrice(),
            shippingFee,
            refundAccount);
    if (!participationDomainService.createParticipationIfRecruiting(participation)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.C2C));
    return new ParticipateResult(participation.getId(), participation.getTotalAmount(), null, null);
  }

  /**
   * C2C 추가 모집 — 성사 확정 후 빈 슬롯 즉시입금 진입 (docs/46 §4.7-E1). 분철은 이미 성사됐으므로 개별 24h 기한으로 바로 입금
   * 대기(AWAITING_PAYMENT)에 들어가고, 계좌는 확정 시점 스냅샷(§4.7-B1)을 안내한다.
   */
  private ParticipateResult joinCollectingC2c(
      final Buncheol buncheol,
      final Long participantId,
      final BuncheolMember member,
      final Long shippingAddressId,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant now) {
    Instant dueAt = now.plus(C2C_PAYMENT_WINDOW);
    Participation participation =
        Participation.create(
            buncheol.getId(),
            member.getId(),
            participantId,
            shippingAddressId,
            member.getPrice(),
            shippingFee,
            refundAccount,
            dueAt);
    if (!participationDomainService.createParticipationIfCollecting(participation)) {
      // 진행확정(CONFIRMED) 전이 직후 등 — 추가 모집이 이미 닫힘.
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.C2C));
    return new ParticipateResult(
        participation.getId(), participation.getTotalAmount(), dueAt, buncheol.getPaymentAccount());
  }

  /**
   * 개최자의 수동 입금확인 (AWAITING_PAYMENT → CONFIRMED). 입금 기한(30분 칼컷) 내에만 가능하다. 입금확인 시점에 배송지를 스냅샷(Delivery)으로
   * 박제한다. 이 입금확인으로 분철의 모든 멤버 슬롯이 입금확인되면(매진+전원확정) deadline 전이라도 분철을 진행확정으로 조기 전이한다.
   */
  @Transactional
  public void confirmPayment(final Long hostId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);

    doConfirmPayment(participation, buncheol);
  }

  /** 관리자(운영자)의 입금확인. 개최자 소유권 검증 없이 모든 분철의 참여를 확인할 수 있다는 점만 다르다. */
  @Transactional
  public void confirmPaymentByAdmin(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());

    doConfirmPayment(participation, buncheol);
  }

  /**
   * 외부 입금 연동(입금 웹훅)의 자동 입금확인. 수동 경로와 달리 실패를 예외로 던지지 않고 {@link SystemPaymentConfirmResult} 로 돌려준다 —
   * 웹훅은 재전송되므로 이미 확정된 건을 오류로 응답하면 발신 측이 재전송을 반복하고, 기한이 지나 확정 불가한 건은 운영자 개입이 필요해 구분해야 한다.
   * 상태 전이는 수동 경로와 동일한 CAS 를 거치므로 만료 스케줄러와 동시에 실행돼도 한쪽만 성공한다.
   *
   * <p>격리수준을 READ_COMMITTED 로 낮춘 이유: MySQL 기본값인 REPEATABLE READ 에서는 첫 조회가 트랜잭션의 consistent read view 를
   * 확정해, CAS 실패 후 재조회해도 시작 시점 스냅샷을 본다. 그러면 다른 트랜잭션(운영자 수동확인·병렬 웹훅)이 먼저 확정한 건을 여전히
   * {@code AWAITING_PAYMENT} 로 읽어 {@code NOT_CONFIRMABLE} 로 오분류하고, 정상 확정된 참여에 "환불 확인 필요" 오탐 알림이 나간다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SystemPaymentConfirmResult confirmPaymentBySystem(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    final Instant now = Instant.now(clock);

    // C2C 는 페이액션 주문을 등록하지 않아 정상 경로에선 웹훅이 오지 않지만, 방어적으로 자동확정을 차단한다 (docs/46 §3-2).
    // NOT_CONFIRMABLE 로 돌려 운영자 슬랙 알림 경로를 태운다 — 도달했다면 조사할 이상 신호다.
    if (buncheol.isC2c()) {
      return SystemPaymentConfirmResult.NOT_CONFIRMABLE;
    }

    if (!participationDomainService.confirmPaymentIfAwaiting(participationId, now)) {
      return participationDomainService.getParticipation(participationId).getStatus()
              == ParticipationStatus.CONFIRMED
          ? SystemPaymentConfirmResult.ALREADY_CONFIRMED
          : SystemPaymentConfirmResult.NOT_CONFIRMABLE;
    }

    applyPaymentConfirmed(participation, buncheol, now);
    return SystemPaymentConfirmResult.CONFIRMED;
  }

  private void doConfirmPayment(final Participation participation, final Buncheol buncheol) {
    final Instant now = Instant.now(clock);
    if (buncheol.isC2c()) {
      // C2C 는 "보냈어요"(PAYMENT_SENT) 상태도 확인 대상이고, 개최자 확인이 늦어도 유효하도록 기한 경과 검사를
      // 하지 않는다 (docs/46 §3-6).
      participationDomainService.confirmPaymentPayable(participation.getId(), now);
    } else {
      participationDomainService.confirmPayment(participation.getId(), now);
    }
    applyPaymentConfirmed(participation, buncheol, now);
  }

  /** 입금확인 CAS 성공 이후의 부수효과. 수동·자동 확인이 공유한다. */
  private void applyPaymentConfirmed(
      final Participation participation, final Buncheol buncheol, final Instant now) {
    // 입금확인 시점에 배송지를 스냅샷으로 확정한다. 배송지는 참여 후 변경 불가(updatable=false)라 참여 시점 값이 그대로 유효하다.
    deliverySnapshotCreator.create(participation);
    eventPublisher.publishEvent(new PaymentConfirmedEvent(participation.getId()));

    if (buncheol.isC2c()) {
      confirmBuncheolIfAllCollected(buncheol, now);
    } else {
      confirmBuncheolIfAllSlotsConfirmed(buncheol, now);
    }
  }

  /**
   * C2C: 이 입금확인으로 미확정 활성 참여가 더 없으면(전원 입금확인) 분철을 PAYMENT_COLLECTING → CONFIRMED 로 전이한다 (docs/46
   * §4.3). 판정·전이는 CAS 서브쿼리로 원자화돼 마지막 두 건을 동시에 확인하는 경합에서도 한쪽만 후속(진행확정 알림)을 수행한다.
   */
  private void confirmBuncheolIfAllCollected(final Buncheol buncheol, final Instant now) {
    if (buncheolDomainService.confirmIfAllCollected(buncheol.getId(), now)) {
      buncheolConfirmedFinalizer.finalizeConfirmed(buncheol.getId());
    }
  }

  // --- C2C 참여자·개최자 액션 (docs/46 §4.2·§4.4·§4.5) ---

  /**
   * C2C 참여자 "보냈어요" 마킹 (AWAITING_PAYMENT → PAYMENT_SENT, docs/46 §4.2). 기한 경과 검사 없음 — 기한 직전 입금
   * 보호가 목적이며, 만료 스케줄러가 먼저 취소했으면 상태 위반으로 안내한다. 더블탭 등 재요청은 멱등 처리한다.
   */
  @Transactional
  public void markPaymentSent(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    requireC2c(participation);

    if (participationDomainService.markPaymentSent(participationId, now)) {
      return;
    }
    if (participationDomainService.getParticipation(participationId).getStatus()
        == ParticipationStatus.PAYMENT_SENT) {
      return; // 멱등: 이미 마킹됨
    }
    throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
  }

  /**
   * C2C 참여자 마킹 철회 (PAYMENT_SENT → AWAITING_PAYMENT 복귀, 기한 유지 — 오마킹 셀프 수정). {@code paymentSentAt}
   * 은 분쟁 증거로 보존된다. 이미 입금 대기로 돌아가 있으면 멱등 처리한다.
   */
  @Transactional
  public void revertPaymentSent(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    requireC2c(participation);

    if (participationDomainService.revertPaymentSent(
        participationId, participation.getDueAt(), now)) {
      return;
    }
    if (participationDomainService.getParticipation(participationId).getStatus()
        == ParticipationStatus.AWAITING_PAYMENT) {
      return; // 멱등: 이미 철회됨
    }
    throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
  }

  /**
   * C2C 개최자 미입금 반려 (docs/46 §4.5 — 취소가 아니라 재입금 기회). PAYMENT_SENT 를 AWAITING_PAYMENT 로 되돌리고 기한을
   * max(기존, now+24h) 로 연장한 뒤, 커밋 후 재확인 알림(연장된 새 기한 포함)을 트리거한다.
   */
  @Transactional
  public void rejectPaymentSent(final Long hostId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    Instant extended = now.plus(C2C_PAYMENT_WINDOW);
    Instant newDueAt =
        participation.getDueAt() != null && participation.getDueAt().isAfter(extended)
            ? participation.getDueAt()
            : extended;
    if (!participationDomainService.revertPaymentSent(participationId, newDueAt, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
    }

    eventPublisher.publishEvent(new PaymentRecheckRequestedEvent(participationId));
  }

  /**
   * C2C 참여자 자발 취소 (docs/46 §5). 신청(APPLIED)·입금 대기(AWAITING_PAYMENT)에서만 허용하고, 보냈어요·입금확인 이후는 돈이
   * 개최자에게 간 구간이라 문의 경유로 안내한다(§5 구간 ②′·③).
   */
  @Transactional
  public void cancelByParticipant(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    requireC2c(participation);

    if (!participationDomainService.cancelByUser(participationId, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);
    }
  }

  /** C2C 전용 액션 가드 — LEGACY 참여에는 새 플로우 API 를 제공하지 않는다 (docs/46 §4.4). */
  private void requireC2c(final Participation participation) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
  }

  /**
   * 분철의 모든 멤버 슬롯이 입금확인(CONFIRMED)됐으면 deadline 전이라도 분철을 진행확정으로 조기 전이한다. 매진+전원확정이면 어차피 마감 시점에
   * 진행확정될 운명이라 결과는 같고 시점만 앞당긴다. 매진·최소인원 판정과 {@code RECRUITING → CONFIRMED} 전이를 CAS UPDATE 서브쿼리로
   * 원자화(current read)했으므로, 마지막 슬롯을 동시에 입금확인하는 경합에서도 조기 확정을 놓치지 않고 선점한 한쪽만 후속(진행확정 알림)을 수행한다.
   *
   * <p>전 슬롯 확정이라도 입금확인 인원이 최소 진행 인원에 못 미치면(슬롯 수 &lt; minHeadcount 인 분철) CAS 가 전이하지 않고, deadline 마감
   * 스케줄러의 취소 판정에 맡긴다.
   */
  private void confirmBuncheolIfAllSlotsConfirmed(final Buncheol buncheol, final Instant now) {
    long totalSlots = buncheolMemberDomainService.findAllByBuncheolId(buncheol.getId()).size();
    if (buncheolDomainService.confirmIfAllSlotsConfirmed(buncheol.getId(), totalSlots, now)) {
      buncheolConfirmedFinalizer.finalizeConfirmed(buncheol.getId());
    }
  }

  private Instant paymentDueAt(final Instant now, final Instant deadline) {
    Instant windowEnd = now.plus(PAYMENT_WINDOW);
    return windowEnd.isBefore(deadline) ? windowEnd : deadline;
  }
}
