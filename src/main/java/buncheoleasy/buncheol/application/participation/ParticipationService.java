package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.BuncheolConfirmedFinalizer;
import buncheoleasy.buncheol.application.BuncheolFullEvent;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.code.ParticipationCode;
import buncheoleasy.buncheol.domain.code.ParticipationCodeDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
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
import java.util.List;
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
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ParticipationCodeDomainService participationCodeDomainService;
  private final ParticipationShippingAddressResolver participationShippingAddressResolver;
  private final UserDomainService userDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 참여 신청. 요청이 슬롯을 여러 개 담고 있으면 <b>한 트랜잭션에서</b> 전부 만든다.
   *
   * <p>🔴 <b>하나라도 실패하면 전체 롤백</b>이다 (docs/70 §5). 3개 중 하나가 이미 팔렸으면 나머지 둘도 만들지
   * 않는다 — 부분 성공을 남기면 참여자가 "몇 개가 잡혔는지" 를 화면에서 재구성해야 한다. 조건부 INSERT 가
   * 0행을 돌리면 예외가 나므로 {@code @Transactional} 이 그대로 롤백한다.
   *
   * <p><b>배송비·묶음은 자연히 맞는다.</b> 2번째 슬롯부터는 {@code findFirstActiveInBuncheol} 이 <b>방금 만든
   * 첫 슬롯</b>을 찾아내 같은 묶음을 재사용하고 배송비를 0으로 둔다 — 슬롯 하나씩 N번 신청한 것과 결과가 같다.
   * 그래서 루프가 묶음 id 를 따로 이어 나를 필요가 없다.
   *
   * <p>⚠️ 다중 슬롯은 <b>C2C 에서만</b> 열린다. LEGACY 는 1인 1활성슬롯이 DB 유니크로 강제돼 있어 2번째
   * INSERT 가 그 자리에서 막힌다 — 요청 단계에서 먼저 거절해 원인이 드러나게 한다.
   */
  @Transactional
  public ParticipateResult participate(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    List<Long> slotIds = request.slotIds();
    if (slotIds.isEmpty()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }
    if (slotIds.size() == 1) {
      return participateSingle(buncheolId, participantId, request, slotIds.get(0));
    }

    // 다중은 C2C 전용이다. LEGACY 에서 2번째가 유니크에 막히면 "이미 참여했다" 로 보여 원인이 안 드러난다.
    if (!buncheolDomainService.getBuncheol(buncheolId).isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
    // 코드는 슬롯 하나에 대응한다 — 여러 슬롯에 같은 코드를 쓸 수 없다.
    if (ParticipationCodeDomainService.submitted(request.participationCode())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
    }

    List<ParticipateResult> results =
        slotIds.stream()
            .map(slotId -> participateSingle(buncheolId, participantId, request, slotId))
            .toList();
    // 첫 슬롯이 배송비를 지므로 총액은 합산해야 한다. 기한·계좌는 묶음 단위라 전부 같다.
    ParticipateResult first = results.get(0);
    return new ParticipateResult(
        first.participationId(),
        results.stream().map(ParticipateResult::participationId).toList(),
        results.stream().mapToLong(ParticipateResult::totalAmount).sum(),
        first.dueAt(),
        first.hostAccount());
  }

  private ParticipateResult participateSingle(
      final Long buncheolId,
      final Long participantId,
      final ParticipateRequest request,
      final Long slotId) {
    final Instant now = Instant.now(clock);

    // 가입 미완료(전화번호 미등록) 유저 차단. 배송 스냅샷이 phoneNumber 를 요구하므로 참여 진입 자체를 막는다.
    userDomainService.requireProfileCompleted(participantId);

    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);

    // C2C 는 신청(무입금)→확정→입금 플로우라 별도 경로로 처리한다. LEGACY 경로는 이하 현행 그대로 (docs/46 §0.1-3).
    if (buncheol.isC2c()) {
      return participateC2c(buncheol, participantId, request, slotId, now);
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
    BuncheolMember member = buncheolMemberDomainService.getBuncheolMember(slotId, buncheolId);

    Optional<ParticipationCode> code =
        participationCodeDomainService.validateForParticipation(
            member, request.participationCode(), now);

    ShippingAddress shippingAddress =
        participationShippingAddressResolver.resolve(
            participantId, buncheol, request.shippingAddressId());
    // 0으로 눌러 두지 않으면 "0원 슬롯 + 배송비 있음" 이 되어 배송비 환급 이벤트 대상으로 잡힌다
    // (ShippingFeePaybackPolicy) — 서포터즈 참여내역에 없는 환급 CTA 가 붙는다.
    long shippingFee =
        code.isPresent() ? 0L : buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    Instant dueAt = paymentDueAt(now, buncheol.getDeadline());
    // 금액과 무관하게 계좌를 요구한다 (docs/80 결정 1). 0원이라 환불할 돈은 없지만, 참여 묶음
    // (participation_bundles.refund_*)이 NOT NULL 이라 계좌 없는 참여는 묶음을 만들 수 없다 — 두 테이블이
    // 반대로 말하면 그 컬럼을 읽는 13곳이 각자 종류를 판단해야 하고, 하나만 빠뜨려도 500 이다(실제로 빠뜨렸다).
    // C2C 는 이미 같은 규칙이다(아래 participateC2c).
    RefundAccount refundAccount = refundAccountSnapshot(participantId);

    // 0원 참여는 아래에서 분철 조기 확정 CAS(X 락)까지 간다. 참여 INSERT 가 buncheols 에 공유 락을
    // 걸므로 그대로 두면 한 트랜잭션 안에서 S→X 업그레이드가 생겨 동시 참여끼리 데드락이 난다.
    // C2C 와 같은 방향(분철 → 참여)으로 X 를 선취해 업그레이드를 없앤다.
    if (member.getPrice() == 0L && shippingFee == 0L) {
      buncheolDomainService.getBuncheolForUpdate(buncheolId);
    }

    Participation participation =
        Participation.create(
            buncheolId,
            member.getId(),
            participantId,
            shippingAddress.getId(),
            member.getPrice(),
            shippingFee,
            dueAt);
    // 저장 시점에도 분철이 모집중인지 원자적으로 재확인(없으면 false → 롤백). 멤버 슬롯이 이미 점유됐으면 DuplicateKey →
    // PARTICIPATION_ALREADY_EXISTS 로 롤백.
    if (!participationDomainService.createParticipationIfRecruiting(participation)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // LEGACY 는 1인 1활성슬롯이라 묶음이 곧 참여다 — 묶을 것이 애초에 없어 항상 새로 연다(백필 STEP 1 과 같은 규칙).
    participationBundleDomainService.attach(
        participation, null, shippingAddress.getId(), shippingFee, refundAccount, dueAt, now);

    // 참여 INSERT 이후여야 코드에 참여 id 를 남길 수 있고, 슬롯 점유 실패 시 코드도 함께 롤백된다.
    code.ifPresent(it -> participationCodeDomainService.consume(it, participation.getId(), now));

    // 개최자(MVP 운영자)가 입금 기한 내에 확인·입금확인할 수 있도록 커밋 후 운영자 슬랙 채널로 신규 참여를 알린다.
    eventPublisher.publishEvent(
        new ParticipationCreatedEvent(participation.getId(), FlowType.LEGACY));

    if (participation.isFree()) {
      return confirmFreeParticipation(participation, buncheol, now);
    }

    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    return ParticipateResult.single(
        participation.getId(), participation.getTotalAmount(), dueAt, hostAccount);
  }

  /**
   * 0원 참여는 참여 즉시 입금확인까지 진행한다 — 아무도 입금할 수 없는 참여를 입금 대기로 두면 30분 뒤 만료 스케줄러가 취소한다. 수동 입금확인과 같은 CAS·같은
   * 부수효과를 그대로 태운다. 응답에 계좌·기한을 싣지 않아야 입금 안내 화면이 뜨지 않는다.
   */
  private ParticipateResult confirmFreeParticipation(
      final Participation participation, final Buncheol buncheol, final Instant now) {
    participationDomainService.confirmPayment(participation.getId(), now);
    applyPaymentConfirmed(participation, buncheol, now);
    return ParticipateResult.single(
        participation.getId(), 0L, null, null);
  }

  /**
   * C2C 참여 (docs/46 §1.1·§4.7). 모집중(RECRUITING)엔 무입금 신청(APPLIED)으로 슬롯을 선점하고, 성사 확정 후 입금
   * 수집중(PAYMENT_COLLECTING)엔 빈 슬롯에 즉시입금(AWAITING_PAYMENT, 개별 24h)으로 진입한다(추가 모집 — §4.7-E1).
   *
   * <p>다슬롯 일관성(§4.7-A1·A2)은 <b>모집중 구간에만</b> 적용된다: 그 구간의 재참여는 배송지·환불계좌(입금자명)를 강제로 재사용하고
   * 배송비는 첫 참여에만 부과한다 — 한 번의 이체·한 개의 택배이므로 개최자 통장 대조(입금자명 1개)와 배송 1묶음이 성립한다. 요청의 배송지
   * 입력은 무시된다(FE 는 프리필+잠금).
   *
   * <p><b>성사 확정 뒤 추가 모집은 별개 거래다</b> — 새 묶음·새 이체·새 택배라 배송지를 다시 고르고 배송비를 다시 부과하며 입금자명도 그
   * 시점 프로필로 다시 스냅샷한다. A1·A2 의 근거는 무너지지 않는다: 묶음이 나뉘면 이체와 택배도 같이 나뉘므로 <b>이체별로 실제 입금자명이
   * 맞아떨어지는 쪽이 오히려 정확하다</b>(옛 이름을 상속하면 통장에 안 찍힌 이름으로 대조하게 된다).
   */
  private ParticipateResult participateC2c(
      final Buncheol loaded,
      final Long participantId,
      final ParticipateRequest request,
      final Long slotId,
      final Instant now) {
    // 분철 행 락으로 참여 생성을 직렬화한다 — 같은 유저의 동시 다슬롯 신청에서 "첫 참여 판정"(배송비 1회·배송지/입금자명
    // 스냅샷 재사용)이 둘 다 첫 참여로 오판되는 check-then-insert 레이스를 막는다 (docs/46 §4.7-A1·A2).
    // 성사 확정·취소 CAS 와도 직렬화되어 상태 판정이 안정된다. 분철당 참여 빈도가 낮아 락 경합 부담은 미미하다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(loaded.getId());

    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(slotId, buncheol.getId());
    // C2C 에는 코드 슬롯을 만들 수 없다(개최 가드) — 도달했다면 데이터 이상이다.
    if (member.requiresCode()
        || ParticipationCodeDomainService.submitted(request.participationCode())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
    }
    // 상태는 한 번만 읽고 여기서 먼저 거른다. 스냅샷 계산 뒤로 미루면 모집이 끝난 분철에 재참여할 때
    // BUNCHEOL_NOT_RECRUITING 대신 배송지·계좌 예외가 먼저 나가 "왜 계좌 얘기가 나오지" 가 된다.
    //
    // ⚠️ 이 값이 <b>최신이라는 보장은 락이 주지 않는다</b>. 위 getBuncheolForUpdate 는 SELECT ... FOR UPDATE 를
    // 내보내지만, 같은 트랜잭션에서 getBuncheol 이 이미 올려 둔 인스턴스가 영속성 컨텍스트에 있으면 Hibernate 는
    // 그 인스턴스를 그대로 돌려주고 필드를 덮지 않는다 — 즉 락 이전에 읽은 값일 수 있다.
    // 그래도 안전한 이유는 <b>INSERT CAS</b> 다: 상태를 낡게 봐 RECRUITING 으로 오판하면 상속 분기를 타지만
    // createParticipationIfRecruiting 의 조건부 INSERT 가 0행을 돌려 전부 롤백된다. 0원 배송비가 커밋되는
    // 경로는 없다. 돈 판정을 이 값에 매달았으므로 그 근거를 여기 남긴다.
    final BuncheolStatus status = buncheol.getStatus();
    if (status != BuncheolStatus.RECRUITING && status != BuncheolStatus.PAYMENT_COLLECTING) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 🔴 상속은 <b>모집중 재참여에만</b> 적용된다. 성사 확정 뒤 추가 모집은 별도 이체·별도 택배라
    // 배송지를 새로 고르고 배송비를 다시 부과한다 (docs/80 결정 11 · §3-6). 그래서 조회 자체를
    // 모집중에만 한다 — 추가 모집에서는 이 값이 쓰이지 않아 헛쿼리가 된다.
    //
    // ⚠️ 배송지·배송비·입금자명·묶음 재사용 넷이 <b>이 existing 하나</b>에서 나와야 한다. 조건을 두 벌 세우면
    // "배송비는 상속했는데 묶음은 새로" 같은 어긋남이 생기고, 그러면 새 택배에 배송비가 0원으로 굳는다
    // — shipping_fee 와 shipping_address_id 는 updatable=false 라 코드로 되돌릴 수 없다.
    Optional<Participation> existing =
        status == BuncheolStatus.RECRUITING
            ? participationDomainService.findFirstActiveInBuncheol(buncheol.getId(), participantId)
            : Optional.empty();
    final Long shippingAddressId;
    final long shippingFee;
    if (existing.isPresent()) {
      shippingAddressId = existing.get().getShippingAddressId();
      shippingFee = 0L;
    } else {
      ShippingAddress shippingAddress =
          participationShippingAddressResolver.resolve(
              participantId, buncheol, request.shippingAddressId());
      shippingAddressId = shippingAddress.getId();
      shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    }
    // 재사용 후보를 여기서 한 번만 뽑는다 — 배송지·배송비·입금자명·묶음이 같은 existing 에서 나오게.
    Long reusableBundleId = existing.map(Participation::getBundleId).orElse(null);

    // C2C 는 0원 슬롯이어도 예금주를 개최자 통장 대조 키로 쓰므로 금액과 무관하게 계좌를 요구한다.
    //
    // 🔴 단, <b>새 묶음을 열 때만</b> 필요하다. 모집중 재참여는 기존 묶음을 재사용하므로 그 묶음이 이미 가진
    // 계좌가 정본이고, docs/46 §4.7-A2 의 "입금자명 1개" 보장은 이제 <b>값을 복사하는 것이 아니라 묶음을
    // 공유하는 것</b>으로 성립한다 (P2-c).
    //
    // 재사용인데도 스냅샷을 뜨면 ① 유저 조회 헛쿼리가 다슬롯 재참여마다 1건 ② 계좌 강제(PR #151) 이전에
    // 만들어진 활성 C2C 참여를 가진 사람의 재참여가 USER_BANK_ACCOUNT_NOT_REGISTERED 로 <b>새로 막힌다</b>
    // — 묶음을 재사용하니 계좌가 실제로는 필요 없는 경우인데도.
    RefundAccount refundAccount =
        reusableBundleId == null ? refundAccountSnapshot(participantId) : null;

    // 🔴 묶음 경계 판정은 「분철 상태」다 (docs/80 결정 12) — 그리고 그 판정은 <b>아래 switch 자체</b>다.
    // RECRUITING(applyC2c)만 기존 묶음을 재사용하고, PAYMENT_COLLECTING(joinCollectingC2c)은 재사용 후보를
    // 아예 넘겨받지 않는다. 모집중 재참여는 같은 이체·같은 택배지만, 성사 확정 뒤 추가 모집은 별도 이체·별도
    // 택배이기 때문이다. 이 경계가 무너지면 추가 모집이 옛 묶음에 붙어 배송비 재부과(⑤)가 영원히 발동하지 않는다.
    //
    // ❌ 시각 비교(finalized_at)로 짜면 안 된다 — 개최자가 입금 수집 중 취소하면 그 값이 덮어써진다
    // (JpaBuncheolRepository#hostCancelIfCollectingAndNoConfirmed). 백필 SQL 은 시각을 쓰지만 그건 과거를
    // 재구성하는 일회성이고, 여기는 지금 상태를 보면 되므로 더 정확하다.
    //
    // 재사용 후보는 배송지·입금자명을 상속한 그 참여의 묶음이다 — 돈 판정과 묶음 판정이 같은 existing 하나에서
    // 나와야 "배송비는 상속했는데 묶음은 새로" 같은 어긋남이 생기지 않는다.
    // ⚠️ 그 참여의 bundle_id 가 비어 있으면(배포선 창에서 생긴 행) 새로 연다. 이때 배송비는 반드시 위에서
    // 정해진 상속분(0)을 그대로 쓴다 — "새 묶음이니 부과" 로 재계산하면 없던 과금이 생긴다.
    return switch (status) {
      case RECRUITING ->
          applyC2c(
              buncheol,
              participantId,
              member,
              shippingAddressId,
              shippingFee,
              refundAccount,
              reusableBundleId,
              now);
      case PAYMENT_COLLECTING ->
          joinCollectingC2c(
              buncheol,
              participantId,
              member,
              shippingAddressId,
              shippingFee,
              refundAccount,
              now);
      // 위 가드가 이미 걸렀다 — 여기 도달하면 가드가 깨진 것이다. switch 식 exhaustiveness 때문에 남는다.
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
      final Long reusableBundleId,
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
            shippingFee);
    if (!participationDomainService.createParticipationIfRecruiting(participation)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 신청 구간에는 입금 기한이 없다 — 성사 확정 시 묶음에 일괄로 채운다(assignDueAt).
    participationBundleDomainService.attach(
        participation, reusableBundleId, shippingAddressId, shippingFee, refundAccount, null, now);

    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.C2C));
    publishFullIfAllSlotsApplied(buncheol);
    return ParticipateResult.single(
        participation.getId(), participation.getTotalAmount(), null, null);
  }

  /**
   * C2C 정원 충족 감지 — 전 멤버 슬롯이 채워지면 개최자 확정 독촉 알림을 트리거한다 (docs/46 §6). 미충족→충족 전이마다 발행하고, 취소→재신청 루프의 중복
   * 발송 차단은 리스너의 인메모리 가드가 담당한다. 카운트는 반드시 잠금 조회(current read)여야 한다 — participate 진입부 일반 조회가 RR 스냅샷을 행
   * 락 전에 확정하므로, 비잠금 카운트는 락 대기 중 커밋된 타 참여를 못 세어 충족을 놓친다.
   *
   * <p>락 순서 규약: 이 경로는 분철 행(X) → 참여 행(X) 순서다. 개최자 취소 CAS ({@code
   * hostCancelIfCollectingAndNoConfirmed})·데드엔드 정리 CAS({@code cancelIfCollectingAndEmpty}) 도 같은
   * 방향이다. 반면 입금확인은 <b>역순</b>(참여 행 → 분철 행)이다 — LEGACY 뿐 아니라 C2C 도 {@code confirmPaymentPayable} 뒤에
   * {@code confirmIfAllCollected} 로 분철 행을 잡는다. 즉 규약은 이미 한 방향으로 고정돼 있지 않고, 교차 실행 시 정합성은 CAS 가 지키되 실패
   * 모드가 데드락 롤백일 수 있다. 새 경로를 추가할 때 이 두 방향 중 어디에 속하는지 먼저 확인할 것.
   *
   * <p><b>P2-b 가 세 번째 방향을 더했다 — 참여 행 → 묶음 행 → 참여 행.</b> 묶음 종료 CAS 가 활성 슬롯 존재를 UPDATE 의
   * 서브쿼리로 판정하는데(current read) InnoDB 가 그 참여 행들에 공유 락을 건다. 그래서 <b>같은 묶음의 두 슬롯을 동시에
   * 취소하면</b> 서로 상대의 참여 행을 기다려 데드락이 날 수 있다 — 다슬롯 참여자가 두 슬롯을 연타 취소하는 경우다.
   * 정합성은 지켜지고(롤백된 쪽은 취소 자체가 안 된다) 재시도하면 성공하지만, <b>실패 모드가 500 이라는 점</b>은 알고 있어야
   * 한다. 이 판정을 밖으로 빼면 데드락은 사라지지만 그 대신 두 슬롯이 서로의 취소를 못 봐 <b>묶음이 영영 안 닫힌다</b> —
   * 그쪽이 더 나쁘다(조용하고 되돌리기 어렵다).
   *
   * <p>같은 사이클의 쌍이 하나 더 있다 — <b>재참여 ↔ 자발 취소</b>. 재참여는 새 참여 행에 X 를 잡은 뒤 연결 CAS 의
   * {@code EXISTS(묶음)} 로 묶음 행에 S 를 요청하고, 취소는 옛 참여 행에 X 를 잡은 뒤 종료 CAS 로 묶음 행에 X 를
   * 잡고 그 서브쿼리가 새 참여 행에 S 를 요청한다. 원인은 같다 — <b>취소 경로가 분철 행 락을 잡지 않아</b> 두 경로가
   * 직렬화되지 않는다. 레포에 데드락 재시도 핸들러가 없어 실패 모드는 500 이다.
   *
   * <p><b>2026-08-31 추가된 세 경로</b> (이 javadoc 이 요구하는 대로 방향을 밝혀 둔다):
   *
   * <ul>
   *   <li>{@code rejectPaymentSent} — 참여 행(X) → 묶음 행(X). 기존 「참여 → 묶음」 방향과 같다.
   *   <li>{@code ParticipationBundleService#confirmPayment} — ⚠️ <b>참여 행(X) → 분철 행(X) → 다른
   *       묶음 포함 참여 행(S)</b>. {@code confirmIfAllCollected} 의 서브쿼리가 그 분철의 참여 전체에
   *       공유 락을 건다. <b>같은 분철의 두 묶음을 개최자가 연속 확인</b>하면(목록을 위에서 아래로 클릭하는
   *       평범한 조작) Tx A 가 묶음1 슬롯 X → 분철 X 를 쥔 채 묶음2 슬롯 S 를 기다리고, Tx B 는 묶음2 슬롯
   *       X 를 쥔 채 분철 X 를 기다려 <b>데드락</b>이 난다. 슬롯 단위 경로에도 있던 성질이라 새 리스크
   *       클래스는 아니지만, 이 경로는 ① 잡는 행이 슬롯 1개가 아니라 <b>묶음 전건</b>이고 ② 분철 락 <b>전에</b>
   *       배송 스냅샷 N건(조회 2 + INSERT 1 × 슬롯 수)을 처리해 <b>락 보유 시간이 길어 창이 넓다</b>.
   *       창을 줄이려면 {@code confirmIfAllCollected} 를 스냅샷 루프보다 앞으로 당기는 선택지가 있으나,
   *       {@code applyPaymentConfirmed} 의 기존 순서와 달라지므로 별도 판단이 필요하다.
   *   <li>{@code ParticipationBundleService#release} — ⚠️ <b>묶음 행에서 S → X 승격</b>이 일어난다.
   *       {@code releaseBundleIfDue} 의 {@code EXISTS (SELECT b FROM ParticipationBundle ...)} 가
   *       InnoDB 에서 묶음 행에 <b>공유 락</b>을 걸고({@code linkBundleIfUnlinked} 주석과 같은 성질),
   *       이어지는 {@code closeIfEmpty} 가 같은 행의 <b>X 락</b>을 요청한다. 같은 묶음에 「제외」가 동시에
   *       두 번 들어오면(개최자 더블클릭) 각자 S 를 쥔 채 X 를 기다려 <b>데드락</b>이 날 수 있다.
   *       정합성은 CAS 가 지키고 실패 모드는 롤백(500)이라 기존에 문서화된 리스크와 같은 종류다.
   * </ul>
   */
  private void publishFullIfAllSlotsApplied(final Buncheol buncheol) {
    long totalSlots = buncheolMemberDomainService.findAllByBuncheolId(buncheol.getId()).size();
    List<Long> participantIds =
        participationDomainService.findActiveParticipantIdsByBuncheolIdForUpdate(buncheol.getId());
    if (participantIds.size() >= totalSlots) {
      long applicantCount = participantIds.stream().distinct().count();
      eventPublisher.publishEvent(new BuncheolFullEvent(buncheol.getId(), applicantCount));
    }
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
            dueAt);
    if (!participationDomainService.createParticipationIfCollecting(participation)) {
      // 진행확정(CONFIRMED) 전이 직후 등 — 추가 모집이 이미 닫힘.
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 추가 모집은 별도 이체·별도 택배라 항상 새 묶음이다(재사용 후보를 넘기지 않는다). 배송지·배송비도
    // 이 회차 것이다 — 호출부(participateC2c)가 상속을 모집중으로 한정해 여기로는 새 값이 넘어온다.
    // ⚠️ 같은 회차에 슬롯을 여러 개 잡으면 그때마다 새 묶음이라 배송비도 그때마다 붙는다(수용된 한계 —
    // ParticipationBundle javadoc 의 경계 문단).
    participationBundleDomainService.attach(
        participation, null, shippingAddressId, shippingFee, refundAccount, dueAt, now);

    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.C2C));
    return ParticipateResult.single(
        participation.getId(), participation.getTotalAmount(), dueAt, buncheol.getPaymentAccount());
  }

  /**
   * 개최자의 수동 입금확인 (AWAITING_PAYMENT → CONFIRMED). 입금 기한(30분 칼컷) 내에만 가능하다. 입금확인 시점에 배송지를
   * 스냅샷(Delivery)으로 박제한다. 이 입금확인으로 분철의 모든 멤버 슬롯이 입금확인되면(매진+전원확정) deadline 전이라도 분철을 진행확정으로 조기 전이한다.
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
   * 웹훅은 재전송되므로 이미 확정된 건을 오류로 응답하면 발신 측이 재전송을 반복하고, 기한이 지나 확정 불가한 건은 운영자 개입이 필요해 구분해야 한다. 상태 전이는 수동
   * 경로와 동일한 CAS 를 거치므로 만료 스케줄러와 동시에 실행돼도 한쪽만 성공한다.
   *
   * <p>격리수준을 READ_COMMITTED 로 낮춘 이유: MySQL 기본값인 REPEATABLE READ 에서는 첫 조회가 트랜잭션의 consistent read
   * view 를 확정해, CAS 실패 후 재조회해도 시작 시점 스냅샷을 본다. 그러면 다른 트랜잭션(운영자 수동확인·병렬 웹훅)이 먼저 확정한 건을 여전히 {@code
   * AWAITING_PAYMENT} 로 읽어 {@code NOT_CONFIRMABLE} 로 오분류하고, 정상 확정된 참여에 "환불 확인 필요" 오탐 알림이 나간다.
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
   * C2C 참여자 "보냈어요" 마킹 (AWAITING_PAYMENT → PAYMENT_SENT, docs/46 §4.2). 기한 경과 검사 없음 — 기한 직전 입금 보호가
   * 목적이며, 만료 스케줄러가 먼저 취소했으면 상태 위반으로 안내한다. 더블탭 등 재요청은 멱등 처리한다.
   */
  @Transactional
  public void markPaymentSent(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    requireC2c(participation);

    if (participationDomainService.markPaymentSent(participationId, now)) {
      // 실제 전이 시에만 발행 — 멱등 리턴 경로로 옮기면 개최자 알림이 중복된다.
      eventPublisher.publishEvent(new PaymentSentEvent(participationId));
      return;
    }
    if (participationDomainService.getParticipation(participationId).getStatus()
        == ParticipationStatus.PAYMENT_SENT) {
      return; // 멱등: 이미 마킹됨
    }
    throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
  }

  /**
   * C2C 참여자 마킹 철회 (PAYMENT_SENT → AWAITING_PAYMENT 복귀, 기한 유지 — 오마킹 셀프 수정). {@code paymentSentAt} 은
   * 분쟁 증거로 보존된다. 이미 입금 대기로 돌아가 있으면 멱등 처리한다.
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
    // 셀프 철회와 달리 반려 시각을 남긴다 — 참여자 화면의 "입금 확인 안 됨 · 재확인 필요" 판정 근거 (docs/53 Q-03).
    if (!participationDomainService.rejectPaymentSent(participationId, newDueAt, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
    }
    // 🔴 묶음 기한도 함께 민다. 기한의 정본은 묶음이고(「제외」 가드가 그 값을 본다), 슬롯만 밀면 반려로 기한을
    // 연장받은 정상 입금 대기자를 개최자가 바로 「제외」할 수 있게 된다 — 복구 경로가 문의뿐이다.
    participationBundleDomainService.extendDueAt(participation.getBundleId(), newDueAt, now);

    eventPublisher.publishEvent(new PaymentRecheckRequestedEvent(participationId));
  }

  /**
   * C2C 참여자 자발 취소 (docs/46 §5 + docs/56 H-09). 신청(APPLIED)과 <b>성사 확정을 거치지 않은</b> 입금
   * 대기(AWAITING_PAYMENT)에서만 허용한다. 보냈어요·입금확인 이후는 돈이 개최자에게 간 구간이라 문의 경유로 안내한다(§5 구간 ②′·③).
   *
   * <p>docs/56 H-09 로 §5 구간 ②(확정 후 입금 대기)의 자발 취소를 닫았다 — 개최자가 성사 확정으로 인원을 계산한 뒤 참여자가 빠져나가는 것을 막는다.
   * 다만 "확정 후 입금 대기" 상태에는 성사 확정을 거치지 않고 도달하는 경로가 하나 더 있다: 입금 수집중 분철의 추가 모집(docs/46 §4.7-E1)은 신청 즉시
   * AWAITING_PAYMENT 로 생성된다. 상태만 보고 막으면 이 경로로 들어온 참여자는 신청하는 순간 24시간 잠겨 오신청조차 되돌릴 수 없으므로, {@link
   * Buncheol#isCreatedBeforeFinalize} 로 <b>성사 확정을 거친 참여만</b> 막는다.
   *
   * <p>판정 자체는 {@link ParticipationCancellability#of} 가 하고 여기서는 사유를 에러코드로 바꾸기만 한다 — 참여 조회 응답이 같은 판정을
   * 그대로 내려주므로(docs/56 S-1) 화면의 취소 버튼과 이 게이트가 갈리지 않는다.
   */
  @Transactional
  public void cancelByParticipant(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    requireCancellable(ParticipationCancellability.of(participation, buncheol));

    if (!participationDomainService.cancelByUser(participationId, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);
    }

    // 이 슬롯이 마지막이었으면 묶음도 끝난다. 안 닫으면 「죽었는데 활성」 묶음이 남고, 그 사람이 재참여할 때
    // 시체 묶음을 재사용해 택배가 옛 주소로 나간다. 판정은 CAS 가 한다(동시 취소 안전 — 포트 javadoc).
    participationBundleDomainService.closeIfEmpty(participation.getBundleId(), now);
  }

  /**
   * 취소 불가 사유를 에러코드로 바꾼다. 매핑을 <b>switch 식</b>으로 두어야 사유가 추가될 때 컴파일 에러로 잡힌다 — enum 을 켜는 switch
   * <b>문</b>은 exhaustiveness 검사를 받지 않아(JLS §14.11.2), 새 사유가 아무 분기도 타지 않고 통과해 <b>취소가 조용히 허용</b>된다
   * (자발 취소 CAS 는 상태만 보므로 그대로 성공한다). fail-open 이라 문 형태를 쓰면 안 된다.
   */
  private static void requireCancellable(final ParticipationCancellability cancellability) {
    ErrorCode errorCode =
        switch (cancellability) {
          case CANCELLABLE -> null;
          case BLOCKED_BY_STATUS -> ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED;
          case FLOW_NOT_SUPPORTED -> ErrorCode.PARTICIPATION_CANCEL_NOT_SUPPORTED;
          case BLOCKED_BY_HOST_CONFIRM -> ErrorCode.PARTICIPATION_CANCEL_AFTER_HOST_CONFIRM;
        };
    if (errorCode != null) {
      throw new BusinessException(errorCode);
    }
  }

  /**
   * C2C 전용 액션 가드 — LEGACY 참여에는 새 플로우 API 를 제공하지 않는다 (docs/46 §4.4).
   *
   * <p>{@link ErrorCode#BUNCHEOL_FLOW_NOT_SUPPORTED} 는 C2C 전용 액션 여러 곳이 공유하는 범용 문구다. 사용자가 누른 버튼이
   * 특정되는 경로(자발 취소)는 이 헬퍼를 쓰지 않고 전용 코드로 직접 던진다 (docs/53 Q-12 와 같은 유형의 혼선 방지).
   */
  private void requireC2c(final Participation participation) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
  }

  /**
   * 분철의 모든 멤버 슬롯이 입금확인(CONFIRMED)됐으면 deadline 전이라도 분철을 진행확정으로 조기 전이한다. 매진+전원확정이면 어차피 마감 시점에 진행확정될
   * 운명이라 결과는 같고 시점만 앞당긴다. 매진·최소인원 판정과 {@code RECRUITING → CONFIRMED} 전이를 CAS UPDATE 서브쿼리로
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

  /**
   * 참여 시점의 마이페이지 정산 계좌를 환불 계좌로 굳힌다. 서비스 전체에서 계좌 입력 경로는 마이페이지 하나뿐이라 요청 본문으로 받지 않는다.
   *
   * <p>참여마다 스냅샷을 남기는 이유는 예금주가 곧 <b>입금 매칭 키(입금자명)</b> 이기 때문이다 — 입금 대기 중인 참여의 입금자명이 마이페이지 계좌 수정으로 바뀌면
   * 통장 대조가 깨진다. 최소 자릿수 검증({@code validateForRegistration})은 등록 시점 규칙이라 여기서 다시 걸지 않는다 — 규칙 도입 전에 저장된
   * 계좌를 가진 유저의 참여가 막힌다.
   */
  private RefundAccount refundAccountSnapshot(final Long participantId) {
    BankAccount bankAccount = userDomainService.getUser(participantId).getBankAccount();
    if (bankAccount == null) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
    return RefundAccount.of(bankAccount.bank(), bankAccount.account(), bankAccount.holder());
  }

  private Instant paymentDueAt(final Instant now, final Instant deadline) {
    Instant windowEnd = now.plus(PAYMENT_WINDOW);
    return windowEnd.isBefore(deadline) ? windowEnd : deadline;
  }
}
