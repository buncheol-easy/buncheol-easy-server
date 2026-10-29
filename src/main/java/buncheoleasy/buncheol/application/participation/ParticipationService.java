package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.BuncheolConfirmedFinalizer;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.application.PaymentStarter;
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
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSetting;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingDomainService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

  // 운영진 분철 참여(개최자 계좌 노출) 시점부터 입금 만료까지. 단, deadline 을 넘지 않도록 클램프한다.
  private static final Duration STAFF_PAYMENT_TIME_LIMIT = Duration.ofMinutes(10);

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ParticipationCodeDomainService participationCodeDomainService;
  private final StaffBuncheolSettingDomainService staffBuncheolSettingDomainService;
  private final ParticipationShippingAddressResolver participationShippingAddressResolver;
  private final UserDomainService userDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final PaymentStarter paymentStarter;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 참여 신청. 요청이 슬롯을 여러 개 담고 있으면 <b>한 트랜잭션에서</b> 전부 만든다.
   *
   * <p>🔴 <b>하나라도 실패하면 전체 롤백</b>이다 (docs/70 §5). 3개 중 하나가 이미 팔렸으면 나머지 둘도 만들지
   * 않는다 — 부분 성공을 남기면 참여자가 "몇 개가 잡혔는지" 를 화면에서 재구성해야 한다. 조건부 INSERT 가
   * 0행을 돌리면 예외가 나므로 {@code @Transactional} 이 그대로 롤백한다.
   *
   * <p><b>배송비·묶음은 자연히 맞는다.</b> 2번째 슬롯부터는 상속 원본 조회({@code
   * ParticipationDomainService#findInheritanceSource} — 모집 중은 살아 있는 참여, 진행 확정 뒤는 신청(APPLIED)만)가 <b>방금
   * 만든 첫 슬롯</b>을 찾아내 같은 묶음을 재사용하고 배송비를 0으로 둔다 — 슬롯 하나씩 N번 신청한 것과 결과가 같다.
   * 그래서 루프가 묶음 id 를 따로 이어 나를 필요가 없다.
   *
   * <p>신청은 모집중(RECRUITING)과 회원 개최 추가 모집 중(진행 확정 + 스위치 켜짐 + 「종료」 전)에만 받는다. 아래 상태 가드는 락 이전
   * 값이라 안내용이고, 최종 판정은 {@code participateC2c} 가 락을 잡으며 최신 값으로 다시 읽어 한다(BCH-060). 추가 신청이면 요청
   * 끝에서 개최자 알림 이벤트를 <b>한 번</b> 낸다(이번 요청의 참여만).
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
      return publishIfExtra(
          buncheolId, participateSingle(buncheolId, participantId, request, slotIds.get(0), true));
    }

    // 단수 경로와 <b>같은 순서로</b> 검증한다(프로필 → 분철). 순서가 갈리면 전화번호 미등록 유저가 같은
    // 분철에 보냈는데 요청 형태(단수/다중)에 따라 다른 에러 코드를 받아 문의 재현이 어긋난다.
    userDomainService.requireProfileCompleted(participantId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);

    // 다중은 C2C 전용이다. LEGACY 에서 2번째가 유니크에 막히면 "이미 참여했다" 로 보여 원인이 안 드러난다.
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
    // 추가 모집은 「종료」까지 봐야 하지만 여기는 안내용이라 조회하지 않고 스위치까지만 본다(위 운영진 검사 뒤라 회원 개최다).
    if (buncheol.getStatus() != BuncheolStatus.RECRUITING && !buncheol.needsEndedCheck()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    // 코드는 슬롯 하나에 대응한다 — 여러 슬롯에 같은 코드를 쓸 수 없다.
    if (ParticipationCodeDomainService.submitted(request.participationCode())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
    }

    // 🔴 정원 충족 판정(PaymentStarter#startIfFull)은 <b>마지막 슬롯에서만</b> 돌린다. 중간 슬롯에서 정원이 차는
    // 일은 없고(남은 슬롯이 아직 비어 있다), 그 분철의 활성 참여를 FOR UPDATE 로 전건 스캔하는 비싼 판정이 분철 행
    // 락 안에서 돈다(같은 분철의 다른 참여자가 그동안 전부 대기한다).
    List<ParticipateResult> results = new ArrayList<>(slotIds.size());
    for (int i = 0; i < slotIds.size(); i++) {
      final boolean last = i == slotIds.size() - 1;
      results.add(participateSingle(buncheolId, participantId, request, slotIds.get(i), last));
    }
    // 첫 슬롯이 배송비를 지므로 총액은 합산해야 한다. 기한·계좌는 묶음 단위라 전부 같다.
    ParticipateResult first = results.get(0);
    return publishIfExtra(
        buncheolId,
        new ParticipateResult(
            first.participationId(),
            results.stream().map(ParticipateResult::participationId).toList(),
            results.stream().mapToLong(ParticipateResult::totalAmount).sum(),
            first.dueAt(),
            first.hostAccount(),
            first.extra()));
  }

  // 한 요청의 슬롯은 같은 잠금 아래 같은 갈래를 탄다(분철 상태는 요청 안에서 바뀌지 않는다) — 첫 슬롯으로 정한다.
  private ParticipateResult publishIfExtra(final Long buncheolId, final ParticipateResult result) {
    if (result.extra()) {
      eventPublisher.publishEvent(new ExtraAppliedEvent(buncheolId, result.participationIds()));
    }
    return result;
  }

  /**
   * 슬롯 1개 신청. {@code checkFull} 은 이 슬롯 뒤에 정원 충족(입금 단계 시작) 판정을 돌릴지다 — 다중 슬롯 루프는 마지막
   * 슬롯에서만 켠다(호출부 주석 참조). C2C 모집중 경로에서만 의미가 있고 나머지 경로는 무시한다.
   */
  private ParticipateResult participateSingle(
      final Long buncheolId,
      final Long participantId,
      final ParticipateRequest request,
      final Long slotId,
      final boolean checkFull) {
    final Instant now = Instant.now(clock);

    // 가입 미완료(전화번호 미등록) 유저 차단. 배송 스냅샷이 phoneNumber 를 요구하므로 참여 진입 자체를 막는다.
    userDomainService.requireProfileCompleted(participantId);

    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);

    // C2C 는 신청(무입금)→확정→입금 플로우라 별도 경로로 처리한다. LEGACY 경로는 이하 현행 그대로 (docs/46 §0.1-3).
    if (buncheol.isC2c()) {
      return participateC2c(buncheol, participantId, request, slotId, now, checkFull);
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
    // 닫힌 자리는 C2C 개최자 「빼기」로만 생겨 LEGACY 에는 없다 — 데이터 이상 방어(잠금 없음).
    if (member.isClosed()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_MEMBER_CLOSED);
    }

    Optional<ParticipationCode> code =
        participationCodeDomainService.validateForParticipation(
            member, request.participationCode(), now);

    // 예약 오픈은 코드 없는 참여에만 걸린다 — 코드 전용 자리는 오픈 전에도 받는다. 설정 행은 분철과 같은 트랜잭션에서만
    // 생기므로 분철이 보이면 행도 이미 보인다. 앱 시각으로 먼저 거르고, 최종 판정은 아래 INSERT 가 DB 시각으로 한다.
    Optional<StaffBuncheolSetting> staffSetting =
        code.isPresent()
            ? Optional.empty()
            : staffBuncheolSettingDomainService.findSetting(buncheolId);
    if (staffSetting.isPresent() && staffSetting.get().isBeforeOpen(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_OPEN_YET);
    }

    ShippingAddress shippingAddress =
        participationShippingAddressResolver.resolve(
            participantId, buncheol, request.shippingAddressId());
    // 코드 참여는 자리 가격과 관계없이 0원이다. 그래서 코드 전용 자리에도 다른 멤버와 같은 가격을 걸 수 있고, 화면에서
    // 특별한 자리로 드러나지 않는다.
    long amount = code.isPresent() ? 0L : member.getPrice();
    // 0으로 눌러 두지 않으면 "0원 참여 + 배송비 있음" 이 되어 배송비 환급 이벤트 대상으로 잡힌다
    // (ShippingFeePaybackPolicy) — 서포터즈 참여내역에 없는 환급 CTA 가 붙는다.
    long shippingFee =
        code.isPresent() ? 0L : buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    Instant dueAt = paymentDueAt(now, buncheol.getDeadline());
    // 금액과 무관하게 계좌를 요구한다 (docs/80 결정 1). 0원이라 환불할 돈은 없지만, 참여 묶음
    // (participation_bundles.refund_*)이 NOT NULL 이라 계좌 없는 참여는 묶음을 만들 수 없다 — 두 테이블이
    // 반대로 말하면 그 컬럼을 읽는 13곳이 각자 종류를 판단해야 하고, 하나만 빠뜨려도 500 이다(실제로 빠뜨렸다).
    // C2C 는 이미 같은 규칙이다(아래 participateC2c).
    RefundAccount refundAccount = refundAccountSnapshot(participantId);

    // 🔴 「이 참여가 0원인가」의 단일 판정. 이 메서드가 두 번 쓴다(여기 · 아래 조기 확정 분기).
    // 저장된 행으로 판정하면 안 된다 — participations.shipping_fee 쓰기를 빼면 그 컬럼은 새 행에서
    // 항상 0 이라, 「상품 0원 + 배송비만 내는」 참여가 전부 무료로 뒤집혀 입금 없이 즉시 확정된다.
    // 방금 이 메서드가 정한 두 값으로만 판정한다.
    final boolean free = amount == 0L && shippingFee == 0L;

    // 0원 참여는 아래에서 분철 조기 확정 CAS(X 락)까지 간다. 참여 INSERT 가 buncheols 에 공유 락을
    // 걸므로 그대로 두면 한 트랜잭션 안에서 S→X 업그레이드가 생겨 동시 참여끼리 데드락이 난다.
    // C2C 와 같은 방향(분철 → 참여)으로 X 를 선취해 업그레이드를 없앤다.
    if (free) {
      buncheolDomainService.getBuncheolForUpdate(buncheolId);
    }

    Participation participation =
        Participation.create(
            buncheolId,
            member.getId(),
            participantId,
            shippingAddress.getId(),
            amount,
            shippingFee,
            dueAt);
    // 저장 시점에도 분철이 모집중인지 원자적으로 재확인(없으면 false → 롤백). 멤버 슬롯이 이미 점유됐으면 DuplicateKey →
    // PARTICIPATION_ALREADY_EXISTS 로 롤백. 오픈 조건 INSERT 는 설정 행이 있는 분철에만 쓴다 — 행이 없는 분철에 NOT EXISTS 를
    // 돌리면 없는 행 자리에 간격 잠금이 걸린다.
    boolean inserted =
        staffSetting.isPresent()
            ? participationDomainService.createParticipationIfRecruitingAndOpen(participation)
            : participationDomainService.createParticipationIfRecruiting(participation);
    if (!inserted) {
      // 0행의 원인을 INSERT 와 같은 DB 시계로 가른다. 앱 시계로 보면 정각 직후 시계 차이 동안 BCH-060 으로 잘못 나간다.
      if (staffSetting.isPresent()
          && staffBuncheolSettingDomainService.isBeforeOpenByDbClock(buncheolId)) {
        throw new BusinessException(ErrorCode.BUNCHEOL_NOT_OPEN_YET);
      }
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // LEGACY 는 1인 1활성슬롯이라 묶음이 곧 참여다 — 묶을 것이 애초에 없어 항상 새로 연다(백필 STEP 1 과 같은 규칙).
    // 🔴 "LEGACY 는 상속하지 않는다" 는 판정은 여기 인라인이 아니라 {@code
    // ParticipationDomainService#inheritanceAppliesTo} 가 갖고 있다 — 화면(분철 상세 응답)도 같은 판정을 써야
    // 하므로 게이트를 여기서 다시 세우지 마라.
    participationBundleDomainService.attach(
        participation, null, shippingAddress.getId(), shippingFee, refundAccount, dueAt, now);

    // 참여 INSERT 이후여야 코드에 참여 id 를 남길 수 있고, 슬롯 점유 실패 시 코드도 함께 롤백된다.
    code.ifPresent(it -> participationCodeDomainService.consume(it, participation.getId(), now));

    // 개최자(MVP 운영자)가 입금 기한 내에 확인·입금확인할 수 있도록 커밋 후 운영자 슬랙 채널로 신규 참여를 알린다.
    eventPublisher.publishEvent(
        new ParticipationCreatedEvent(participation.getId(), FlowType.LEGACY));

    if (free) {
      return confirmFreeParticipation(participation, buncheol, now);
    }

    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    return ParticipateResult.single(
        participation.getId(), participation.getAmount() + shippingFee, dueAt, hostAccount);
  }

  /**
   * 0원 참여는 참여 즉시 입금확인까지 진행한다 — 아무도 입금할 수 없는 참여를 입금 대기로 두면 10분 뒤 만료 스케줄러가 취소한다. 수동 입금확인과 같은 CAS·같은
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
   * C2C 참여 (docs/46 §1.1·§4.7). 모집중(RECRUITING)에는 무입금 신청(APPLIED)으로 슬롯을 선점한다. 진행 확정 뒤 추가 모집 중이면
   * 같은 APPLIED 로 받는다(추가 신청) — 개최자가 수락해야 입금 단계로 간다. 입금 단계, 추가 모집을 켜지 않았거나 「종료」된 진행 확정은
   * 새 신청을 받지 않는다(BCH-060).
   *
   * <p>다슬롯 일관성(§4.7-A1·A2): 재참여는 배송지·환불계좌(입금자명)를 강제로 재사용하고 배송비는 첫 참여에만 부과한다 — 한 번의
   * 이체·한 개의 택배이므로 개최자 통장 대조(입금자명 1개)와 배송 1묶음이 성립한다. 요청의 배송지 입력은 무시된다(FE 는 프리필+잠금).
   * 추가 신청은 수락 전 추가 신청에만 붙는다 — 입금이 확인된 원래 참여는 배송이 이미 생겨 새 묶음·새 배송비다
   * ({@code ParticipationDomainService#inheritanceAppliesTo}).
   */
  private ParticipateResult participateC2c(
      final Buncheol loaded,
      final Long participantId,
      final ParticipateRequest request,
      final Long slotId,
      final Instant now,
      final boolean checkFull) {
    // 분철 행 락으로 참여 생성을 직렬화한다 — 같은 유저의 동시 다슬롯 신청에서 "첫 참여 판정"(배송비 1회·배송지/입금자명
    // 스냅샷 재사용)이 둘 다 첫 참여로 오판되는 check-then-insert 레이스를 막는다 (docs/46 §4.7-A1·A2).
    // 입금 단계 시작·취소와도 직렬화되어 상태 판정이 안정된다. 분철당 참여 빈도가 낮아 락 경합 부담은 미미하다.
    // 🔴 락과 함께 최신 값으로 다시 읽는다. 락 이전 인스턴스를 그대로 쓰면 락을 기다리는 사이 개최자가 바꾼 조건(신청자 0명일
    // 때의 배송비·마감 수정)을 놓쳐, 옛 배송비가 묶음에 새겨진다 — 묶음 배송비는 updatable=false 라 되돌릴 수 없다.
    Buncheol buncheol = buncheolDomainService.getLatestBuncheolForUpdate(loaded.getId());

    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }
    // 🔴 자리는 <b>잠금 읽기</b>(FOR SHARE)로 읽는다. 이 트랜잭션의 스냅샷은 분철 락 전의 일반 조회
    // (requireProfileCompleted·getBuncheol)가 이미 잡았으므로, 일반 조회로 읽으면 락을 기다리는 사이 개최자가 닫은
    // 자리를 「열림」으로 볼 수 있다. 잠금 읽기는 최신 커밋을 본다. 락 순서는 분철(X) → 자리(S) 로, 개최자 빼기·닫기
    // (분철 X → 자리 X)와 같은 방향이다 — 둘 다 분철 락을 먼저 잡아 자리 락에서 서로를 기다리지 않는다.
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMemberForShare(slotId, buncheol.getId());
    if (member.isClosed()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_MEMBER_CLOSED);
    }
    // C2C 에는 코드 슬롯을 만들 수 없다(개최 가드) — 도달했다면 데이터 이상이다.
    if (member.requiresCode()
        || ParticipationCodeDomainService.submitted(request.participationCode())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
    }
    // 상태는 한 번만 읽고 여기서 먼저 거른다. 스냅샷 계산 뒤로 미루면 모집이 끝난 분철에 재참여할 때
    // BUNCHEOL_NOT_RECRUITING 대신 배송지·계좌 예외가 먼저 나가 "왜 계좌 얘기가 나오지" 가 된다.
    //
    // 상태·스위치는 락 시점 최신이다(위 다시 읽기). 락을 기다리는 사이 입금 단계가 시작됐거나 스위치가 꺼졌으면 받지 않는다. 조건부
    // INSERT 도 같은 조건으로 막지만 더 일찍 나갈 뿐 결과(BCH-060)는 같다.
    // 「종료」 판정은 일반 조회라 락 전 스냅샷을 본다. 「종료」는 한 방향이라 낡은 값은 "아직 종료 아님" 쪽으로만 틀리고, 그때 받은
    // 신청은 수락 대기가 될 뿐이라 개최자가 거절할 수 있다(받아들인 경합).
    final boolean extra =
        buncheol.getStatus() != BuncheolStatus.RECRUITING
            && buncheolDomainService.isExtraRecruiting(buncheol);
    if (buncheol.getStatus() != BuncheolStatus.RECRUITING && !extra) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // ⚠️ 배송지·배송비·입금자명·묶음 재사용 넷이 <b>이 existing 하나</b>에서 나와야 한다. 조건을 두 벌 세우면
    // "배송비는 상속했는데 묶음은 새로" 같은 어긋남이 생기고, 그러면 새 택배에 배송비가 0원으로 굳는다
    // — shipping_fee 와 shipping_address_id 는 updatable=false 라 코드로 되돌릴 수 없다.
    // 🔴 판정을 여기 인라인으로 두지 마라 — 분철 상세 응답이 "이 배송지로 갑니다" 를 그릴 때 같은 판정을
    // 써야 하고, 두 벌이면 화면의 약속과 실제 각인이 갈린다 (findInheritanceSource javadoc).
    Optional<Participation> existing =
        participationDomainService.findInheritanceSource(buncheol, participantId);
    final Long shippingAddressId;
    final long shippingFee;
    if (existing.isPresent()) {
      // 🔴 상속하는 값의 출처는 참여 사본이 아니라 <b>그 참여의 묶음</b>이다. participations 의 두 칸은
      // 정본이 아니고(배송비 #170 · 배송지 이 PR) P4 에서 사라진다 — 사본을 읽으면 신규 행에서 NULL 이
      // 새 묶음에 각인되고, updatable=false 라 코드로 되돌릴 수 없다.
      // 🔴 여기서 null 이 흘러가면 shipping_address_id = NULL 인 <b>새 묶음이 조용히 커밋된다</b>
      // (ParticipationBundle.open 은 이 값을 검증하지 않는다). updatable=false 라 코드로 복구 불가라,
      // 읽기 실패(500)보다 <b>더 비가역</b>이다. 배송 스냅샷과 같은 규약으로 닫는다.
      shippingAddressId =
          participationBundleDomainService.requireInheritedShippingAddressId(existing.get());
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

    // 재사용 후보는 배송지·입금자명을 상속한 그 참여의 묶음이다 — 돈 판정과 묶음 판정이 같은 existing 하나에서
    // 나와야 "배송비는 상속했는데 묶음은 새로" 같은 어긋남이 생기지 않는다.
    // ⚠️ 그 참여의 bundle_id 가 비어 있으면(배포선 창에서 생긴 행) 새로 연다. 이때 배송비는 반드시 위에서
    // 정해진 상속분(0)을 그대로 쓴다 — "새 묶음이니 부과" 로 재계산하면 없던 과금이 생긴다.
    return applyC2c(
        buncheol,
        participantId,
        member,
        shippingAddressId,
        shippingFee,
        refundAccount,
        reusableBundleId,
        now,
        checkFull,
        extra);
  }

  /**
   * C2C 무입금 신청 (APPLIED 슬롯 선점). 신청 단계는 계좌·기한 없이 응답한다 (docs/46 §3-1). {@code extra} 면 진행 확정 뒤 추가
   * 신청이다 — 원래 마감은 이미 지났고, 정원이 차도 아무 전이가 없다.
   */
  private ParticipateResult applyC2c(
      final Buncheol buncheol,
      final Long participantId,
      final BuncheolMember member,
      final Long shippingAddressId,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Long reusableBundleId,
      final Instant now,
      final boolean checkFull,
      final boolean extra) {
    // deadline 경과(마감 판정 대기) 구간의 신규 신청 차단. INSERT 의 원자 조건과 이중 방어.
    if (!extra) {
      buncheol.validateRecruiting(now);
    }

    Participation participation =
        Participation.createApplied(
            buncheol.getId(),
            member.getId(),
            participantId,
            shippingAddressId,
            member.getPrice(),
            shippingFee);
    boolean inserted =
        extra
            ? participationDomainService.createParticipationIfExtraRecruiting(participation)
            : participationDomainService.createParticipationIfRecruiting(participation);
    if (!inserted) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 신청 구간에는 입금 기한이 없다 — 입금 단계 시작 때(추가 신청은 수락 때) 묶음에 채운다.
    participationBundleDomainService.attach(
        participation,
        reusableBundleId,
        shippingAddressId,
        shippingFee,
        refundAccount,
        null,
        extra,
        now);

    eventPublisher.publishEvent(new ParticipationCreatedEvent(participation.getId(), FlowType.C2C));
    if (extra) {
      return ParticipateResult.extraApplied(
          participation.getId(), participation.getAmount() + shippingFee);
    }
    // 마지막 열린 자리가 채워지면 이 요청 안에서 입금 단계가 시작된다. 응답 모양은 그대로다(계좌·기한 null — 안내는 알림톡).
    if (checkFull) {
      paymentStarter.startIfFull(buncheol, now);
    }
    // 🔴 배송비는 반드시 이 메서드가 받은 shippingFee 파라미터여야 한다(묶음의 원값이 아니라).
    // 모집중 재참여면 호출부가 이 값을 0 으로 눌러 넘긴다 — 묶음 원값을 읽으면 2번째 슬롯부터도
    // 만액이 되고, 다슬롯 신청의 합산이 그걸 슬롯 수만큼 곱한다.
    return ParticipateResult.single(
        participation.getId(), participation.getAmount() + shippingFee, null, null);
  }

  /**
   * 개최자의 수동 입금확인 (AWAITING_PAYMENT → CONFIRMED). 입금 기한(10분 칼컷) 내에만 가능하다. 입금확인 시점에 배송지를
   * 스냅샷(Delivery)으로 박제한다. 이 입금확인으로 분철의 모든 멤버 슬롯이 입금확인되면(매진+전원확정) deadline 전이라도 분철을 진행확정으로 조기 전이한다.
   *
   * <p>🔴 <b>LEGACY 전용이다.</b> C2C 는 {@link ParticipationBundleService#confirmPayment} 만 쓴다 —
   * 이유는 {@link #requireLegacy} 참고.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void confirmPayment(final Long hostId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);
    requireLegacy(buncheol);

    doConfirmPayment(participation, buncheol);
  }

  /**
   * 관리자(운영자)의 입금확인. 개최자 소유권 검증 없이 모든 분철의 참여를 확인할 수 있다는 점만 다르다.
   *
   * <p>🔴 여기도 LEGACY 전용이다. 어드민 벌크 확인은 참여 id 배열을 <b>건별 독립 트랜잭션</b>으로 순회하므로
   * (AdminPaymentCommandService), 중간 한 건이 실패해도 앞 건은 이미 커밋된다. C2C 를 허용하면 운영자가
   * 대시보드에서 한 번 잘못 고르는 것만으로 되살릴 수 없는 묶음이 생긴다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void confirmPaymentByAdmin(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    requireLegacy(buncheol);

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

    // C2C 는 페이액션 주문을 등록하지 않아 정상 경로에선 웹훅이 오지 않지만, 방어적으로 자동확정을 차단한다 (docs/46 §3-2).
    // NOT_CONFIRMABLE 로 돌려 운영자 슬랙 알림 경로를 태운다 — 도달했다면 조사할 이상 신호다.
    if (buncheol.isC2c()) {
      return SystemPaymentConfirmResult.NOT_CONFIRMABLE;
    }

    lockBuncheolBeforeConfirm(participation.getBuncheolId());
    final Instant now = Instant.now(clock);

    if (!participationDomainService.confirmPaymentIfAwaiting(participationId, now)) {
      return participationDomainService.getParticipation(participationId).getStatus()
              == ParticipationStatus.CONFIRMED
          ? SystemPaymentConfirmResult.ALREADY_CONFIRMED
          : SystemPaymentConfirmResult.NOT_CONFIRMABLE;
    }

    applyPaymentConfirmed(participation, buncheol, now);
    return SystemPaymentConfirmResult.CONFIRMED;
  }

  /**
   * 슬롯 단위 입금확인의 공통부. 두 진입점이 모두 {@link #requireLegacy} 를 통과시키므로 <b>LEGACY 만 도달한다</b> —
   * C2C 의 "보냈어요 상태도 확인 대상 · 기한 경과 무시" 규칙(docs/46 §3-6)은 묶음 경로가 그대로 이어받았다.
   */
  private void doConfirmPayment(final Participation participation, final Buncheol buncheol) {
    lockBuncheolBeforeConfirm(participation.getBuncheolId());
    final Instant now = Instant.now(clock);
    participationDomainService.confirmPayment(participation.getId(), now);
    applyPaymentConfirmed(participation, buncheol, now);
  }

  /**
   * 슬롯 단위 입금확인은 분철 행부터 잠근다. 확정 CAS(참여 행 X) 뒤의 조기 확정 UPDATE 가 분철 행 X 를 잡고 그 분철의 참여를
   * 전부 세므로, 잠금 없이 들어오면 같은 분철의 동시 확정끼리 REPEATABLE READ(개최자·어드민)에선 데드락이 나고 READ COMMITTED(웹훅)에선
   * 서로의 미커밋 확정을 못 봐 조기 확정이 빠진다. 분철 행에서 줄을 세우면 둘 다 사라진다 — 참여 생성·0원 확정·마감 판정과 같은
   * 분철 → 참여 순서다.
   *
   * <p>그래서 세 진입점 모두 READ COMMITTED 다. REPEATABLE READ 면 잠금 전 첫 조회의 스냅샷이 끝까지 남아, 잠금을 기다리는 사이
   * 커밋된 확정을 진행확정 알림 대상 조회({@code BuncheolConfirmedFinalizer})가 못 보고 빠뜨린다. LEGACY 에서 확정 수를 바꾸는 쓰기는
   * 모두 분철 행을 먼저 잡으므로 READ COMMITTED 에서도 조기 확정 서브쿼리의 개수가 맞다.
   *
   * <p>기한 판정 시각({@code now})은 잠금을 얻은 뒤에 잡는다. 마감 판정 뒤로 줄 선 확인은 now 가 deadline(≥ dueAt)을 넘어 확정 CAS
   * 에 실패해야 끝난 분철에 확정이 새지 않는다.
   */
  private void lockBuncheolBeforeConfirm(final Long buncheolId) {
    buncheolDomainService.getBuncheolForUpdate(buncheolId);
  }

  /**
   * 슬롯 단위 입금확인 CAS 성공 이후의 부수효과. 개최자 수동·어드민·웹훅 자동 확인이 공유한다.
   *
   * <p><b>여기 오는 것은 LEGACY 뿐이다.</b> 진입점은 <b>네 곳</b>이고 걸러 내는 장치가 서로 다르다 —
   * 개최자·어드민 확인은 {@link #requireLegacy}, 웹훅 자동확인은 {@code NOT_CONFIRMABLE} 조기 반환,
   * 0원 참여 즉시확정({@code confirmFreeParticipation})은 {@code participateSingle} 의 플로우 분기다.
   * 넷 중 하나라도 빠뜨리면 이 문단의 결론이 성립하지 않으므로, 호출자를 늘릴 때 함께 갱신할 것. C2C 의 분철 전이(PAYMENT_COLLECTING → CONFIRMED)는
   * {@code BuncheolConfirmer}(개최자 진행 확정, 마지막 묶음 입금 확인)만 한다.
   */
  private void applyPaymentConfirmed(
      final Participation participation, final Buncheol buncheol, final Instant now) {
    // 입금확인 시점에 배송지를 스냅샷으로 확정한다. 배송지는 참여 후 변경 불가(updatable=false)라 참여 시점 값이 그대로 유효하다.
    deliverySnapshotCreator.create(participation);
    eventPublisher.publishEvent(new PaymentConfirmedEvent(participation.getId()));

    confirmBuncheolIfAllSlotsConfirmed(buncheol, now);
  }

  /**
   * C2C 참여자 자발 취소 (docs/46 §5). 신청(APPLIED)에서만 허용한다 — 입금 단계가 시작되면 개최자가 인원을 셈한 뒤라 스스로 빠질 수 없고,
   * 기한 뒤 개최자의 진행 확정 때 입금이 확인되지 않은 참여가 빠진다.
   *
   * <p>판정 자체는 {@link ParticipationCancellability#of} 가 하고 여기서는 사유를 에러코드로 바꾸기만 한다 — 참여 조회 응답이 같은 판정을
   * 그대로 내려주므로(docs/56 S-1) 화면의 취소 버튼과 이 게이트가 갈리지 않는다. 최종 차단은 취소 CAS(신청만)다.
   */
  @Transactional
  public void cancelByParticipant(final Long participantId, final Long participationId) {
    final Instant now = Instant.now(clock);
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);
    // 회원 개최 참여만 분철 행을 첫 잠금으로 잡는다(getBuncheolForUpdate javadoc). 운영진 참여는 아래 판정이 BCH-091 로
    // 거절하므로 잠그지 않는다 — 정각 오픈 때 분철 행 잠금 줄에 거절될 요청을 세우지 않는다. 플로우는 조회한 참여 행의 사본으로 고른다.
    Buncheol buncheol =
        participation.getFlowType() == FlowType.C2C
            ? buncheolDomainService.getBuncheolForUpdate(participation.getBuncheolId())
            : buncheolDomainService.getBuncheol(participation.getBuncheolId());
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
        };
    if (errorCode != null) {
      throw new BusinessException(errorCode);
    }
  }

  /**
   * 🔴 <b>슬롯 단위 입금확인 가드 — C2C 는 묶음 단위로만 확정한다.</b>
   *
   * <p>C2C 의 돈 단위는 참여 한 건이 아니라 <b>묶음</b>이다(이체 1회 · 배송비 1회 · 택배 1개). 그래서 확정도 묶음
   * 통째로여야 하고, 그 경로가 {@link ParticipationBundleService#confirmPayment} 다 — 잠금 조회로 대상을 고정한 뒤
   * 전부 아니면 전무로 CAS 한다.
   *
   * <p>이 가드가 없으면 슬롯 하나만 CONFIRMED 인 <b>혼합 묶음</b>을 만들 수 있다. 이체 1회를 슬롯마다 따로 확인한 기록이 되어
   * 통장 대조가 어긋나고, 묶음 입금확인도 {@code payableStatuses} 집합이 이미 확정된 슬롯을 빼므로 {@code expectedSlotIds} 대조가
   * 어긋나 409 다. 막을 곳은 출구가 아니라 <b>혼합 상태가 생기는 입구</b>다.
   *
   * <p>⚠️ <b>엔드포인트 자체를 지우지도 마라.</b> LEGACY(운영진 개최) 개최 관리 화면이 아직 이 API 를 쓴다.
   * "C2C 만 409" 가 정확한 처방이다.
   *
   * <p>🔴 <b>미연결 행(bundle_id = NULL)을 예외로 빼지 않았다 — 의도된 선택이다.</b> 그런 C2C 행은 이 가드
   * 뒤에 묶음 입금확인을 탈 수 없다 — 묶음 확인은 {@code bundleId} 를 인자로 받아 호출 자체가 불가다. 그럼에도 빼지 않은 근거는
   * 둘이다:
   *
   * <ol>
   *   <li><b>없다</b> — {@code flow_type='C2C' AND bundle_id IS NULL} 인 행이 prod·staging 모두 <b>0건</b>
   *       (2026-09-03 실측, 상태 무관 전 건).
   *   <li><b>생길 수 없다</b> — 참여 생성 트랜잭션에서 {@code linkBundle} 이 실패하면 예외를 던져 <b>참여
   *       INSERT 까지 통째로 롤백된다</b>({@code ParticipationBundleDomainService}). 즉 커밋된 C2C 행은 항상
   *       묶음을 갖는다.
   * </ol>
   *
   * <p>이 전제가 깨지면(P4 이전에 미연결 행이 다시 관측되면) 카브아웃이 아니라 <b>백필</b>이 답이다 — 그 행에
   * 묶음을 붙이면 묶음 경로가 그대로 열린다. 카브아웃하면 {@code confirmPaymentPayable} 체인을 되살려야 하고,
   * 그건 "슬롯 단위 C2C 확정"을 다시 배선 가능한 상태로 만든다.
   */
  private void requireLegacy(final Buncheol buncheol) {
    if (buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNDLE_CONFIRM_REQUIRED);
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
    Instant windowEnd = now.plus(STAFF_PAYMENT_TIME_LIMIT);
    return windowEnd.isBefore(deadline) ? windowEnd : deadline;
  }
}
