package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.C2cHostQualification;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final BuncheolImageDomainService buncheolImageDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final DeliveryDomainService deliveryDomainService;
  private final GroupDomainService groupDomainService;
  private final UserDomainService userDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 분철을 개최한다.
   *
   * <p>⚠️ 여기(또는 {@link #resolveHostFlowType})에 <b>유저 상태 검사를 추가하면 {@link #getHostingEligibility} 도 같이
   * 고쳐야 한다</b> — 두 경로는 판정 조각만 공유하고 조합은 각자 적어, 한쪽만 늘면 컴파일도 테스트도 깨지지 않은 채 조용히 어긋난다. 실제로 정산 계좌 검사가
   * 그렇게 빠져 있었다(docs/53 Q-07 리뷰).
   *
   * @return 생성된 분철 id ({@link buncheoleasy.buncheol.dto.response.HoldBuncheolResponse} 참고)
   */
  @Transactional
  public Long holdBuncheol(
      final Long hostId, final HoldBuncheolRequest request, final List<ImageFile> images) {
    FlowType flowType = resolveHostFlowType(hostId, request.flowType());

    buncheolImageDomainService.validateImageCount(images.size());

    // 대표사진은 이미지 저장 순서를 바꾸지 않고 인덱스 플래그로만 지정한다 (필수 — DTO @NotNull 검증).
    buncheolImageDomainService.validateThumbnailIndex(images.size(), request.thumbnailIndex());

    // 정산 계좌가 등록된 호스트만 분철을 개최할 수 있다 (LEGACY·C2C 공통 — C2C 는 입금 안내 계좌 스냅샷의 원천).
    userDomainService.requireBankAccountRegistered(hostId);

    groupDomainService.validateGroupExists(request.groupId());

    List<Long> memberIds = extractDistinctMemberIds(request.buncheolMembers());
    groupDomainService.getGroupMembersByIdsInGroup(request.groupId(), memberIds);

    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, request.toParams(flowType));

    List<BuncheolMemberParams> memberParams =
        request.buncheolMembers().stream().map(BuncheolMemberRequest::toParams).toList();
    validateCodeOnlySlotsAllowed(flowType, memberParams);
    validateCodeOnlySlotsAreFree(memberParams);
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), memberParams);

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(
          new BuncheolImageUploadEvent(buncheol.getId(), images, request.thumbnailIndex()));
    }

    return buncheol.getId();
  }

  /** 코드 발급 API 가 관리자 전용이라, C2C 에 코드 슬롯을 만들면 아무도 발급할 수 없는 영구 잠긴 슬롯이 된다. */
  private void validateCodeOnlySlotsAllowed(
      final FlowType flowType, final List<BuncheolMemberParams> memberParams) {
    if (flowType == FlowType.LEGACY) {
      return;
    }
    if (memberParams.stream().anyMatch(param -> param.accessType().requiresCode())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_MEMBER_NOT_CODE_ONLY);
    }
  }

  /** 코드 참여는 무상 제공이 전제라 0원 슬롯에만 붙일 수 있다. */
  private void validateCodeOnlySlotsAreFree(final List<BuncheolMemberParams> memberParams) {
    if (memberParams.stream()
        .anyMatch(param -> param.accessType().requiresCode() && param.price() > 0L)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_MEMBER_NOT_FREE);
    }
  }

  /**
   * 개최 방식 결정 + 자격 게이트 (docs/46 §3-8·§7.1-8). 일반 유저 = C2C 강제(LEGACY 요청은 거부 — 페이액션·운영 절차가 붙는 운영진 전용
   * 방식), 운영진(can_host) = LEGACY 기본에 C2C 도 선택 가능. C2C 는 성인·연락처 자격 게이트를 통과해야 한다. 같은 트랜잭션이라 getUser 반복
   * 호출은 영속성 컨텍스트 캐시로 흡수된다.
   */
  private FlowType resolveHostFlowType(final Long hostId, final FlowType requested) {
    if (userDomainService.canHost(hostId)) {
      FlowType resolved = requested != null ? requested : FlowType.LEGACY;
      if (resolved == FlowType.C2C) {
        // C2C 직거래는 개최자 연락처가 분쟁 처리의 근거라, 성인 확인은 건너뛰는 운영진도 가입 완료(전화번호)는 요구한다.
        userDomainService.requireProfileCompleted(hostId);
      }
      return resolved;
    }
    if (requested == FlowType.LEGACY) {
      throw new BusinessException(ErrorCode.USER_CANNOT_HOST);
    }
    userDomainService.requireC2cHostQualification(hostId);
    // 상한은 자격 게이트 통과자에게만 의미가 있으므로 마지막에 검사한다 (운영진은 미적용 — 이벤트 대량 개최 허용).
    buncheolDomainService.validateActiveHostedLimit(hostId);
    return FlowType.C2C;
  }

  /**
   * 개최 자격 사전 조회 (docs/53 Q-07). {@link #holdBuncheol} 이 개최 요청에서 던지는 검사들을 던지지 않는 판정으로 같은 순서대로 재현한다
   * — 순서가 같아야 FE 가 보여주는 사유와 제출 시 실제로 막히는 사유가 일치한다.
   *
   * <p>읽기 전용 조회지만 {@code *QueryService} 로 빼지 않는다 — 재현 대상인 {@link #holdBuncheol}·{@link
   * #resolveHostFlowType} 과 같은 파일에 두어야 한쪽만 바뀌는 것을 알아채기 쉽다.
   *
   * <p>운영진(can_host)은 기본 LEGACY 라 C2C 자격 게이트·상한을 적용하지 않는다. 다만 정산 계좌는 LEGACY·C2C 공통 요구라 모두에게 검사한다.
   * <b>판정은 LEGACY 기준</b>이라, 운영진이 요청에서 C2C 를 선택하는 경우의 추가 요구(가입 완료 — {@link #resolveHostFlowType})는 이
   * 응답에 반영되지 않는다.
   */
  @Transactional(readOnly = true)
  public HostingEligibilityResponse getHostingEligibility(final Long hostId) {
    if (!userDomainService.canHost(hostId)) {
      C2cHostQualification qualification = userDomainService.evaluateC2cHostQualification(hostId);
      if (!qualification.isQualified()) {
        return HostingEligibilityResponse.from(qualification);
      }

      if (buncheolDomainService.isActiveHostedLimitExceeded(hostId)) {
        return HostingEligibilityResponse.blocked(HostingEligibilityResponse.Reason.LIMIT_EXCEEDED);
      }
    }

    // 개최 요청은 자격·상한을 통과한 뒤 정산 계좌를 요구한다(USR-025). 이걸 빼면 계좌 미등록 유저가 폼을 다 채운 뒤 막혀
    // 이 API 가 없애려던 문제가 그대로 남는다.
    if (!userDomainService.hasBankAccount(hostId)) {
      return HostingEligibilityResponse.blocked(
          HostingEligibilityResponse.Reason.BANK_ACCOUNT_REQUIRED);
    }

    return HostingEligibilityResponse.allowed();
  }

  @Transactional
  public void modifyBuncheol(
      final Long hostId,
      final Long buncheolId,
      final BuncheolModifyRequest request,
      final List<ImageFile> images) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    buncheol.validateRecruiting(Instant.now(clock));

    buncheolImageDomainService.validateModifyImageCount(
        buncheolId, request.keepImageIds(), images.size());
    buncheolImageDomainService.validateThumbnailSelection(
        request.keepImageIds(), images.size(), request.thumbnailImageId(), request.thumbnailIndex());

    buncheolDomainService.updateBuncheolContent(buncheol, request.title(), request.description());
    buncheolDomainService.updateBuncheolOpenChatUrl(buncheol, request.openChatUrl());

    // ⚠️ 이 지점 이후는 clearAutomatically=true 벌크 쿼리(삭제·플래그 해제/지정)가 이어져 영속성 컨텍스트가 비워진다.
    // buncheol 엔티티 변경(더티체킹)은 반드시 이 앞에서 끝내야 한다 — 이후 변경은 조용히 유실된다.
    buncheolImageDomainService.deleteImagesExcluding(buncheolId, request.keepImageIds());

    // 대표사진 지정은 필수 — validateThumbnailSelection 이 둘 중 정확히 하나만 있음을 보장한다.
    if (request.thumbnailImageId() != null) {
      // 유지하는 기존 이미지를 대표사진으로 교체한다.
      buncheolImageDomainService.changeThumbnail(buncheolId, request.thumbnailImageId());
    } else {
      // 신규 업로드 이미지가 대표사진이 될 예정 — 기존 플래그만 해제하고, 지정은 커밋 후 업로드 리스너가 수행한다.
      // 업로드가 실패해도 조회 쿼리가 MIN(id) 로 폴백하므로 대표사진이 비지 않는다.
      buncheolImageDomainService.clearThumbnail(buncheolId);
    }

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(
          new BuncheolImageUploadEvent(buncheolId, images, request.thumbnailIndex()));
    }
  }

  /**
   * 오픈채팅 링크만 수정한다. 전체 수정({@link #modifyBuncheol})은 모집중·마감 전으로 묶여 있는데, 링크는 참여자가 입금하며 문의하는
   * 구간에서 오히려 더 필요하다. 그 구간을 열어 주되 가격·멤버 보호 가드는 전체 수정 경로에 그대로 둔다.
   *
   * <p>공백 해석은 {@link Buncheol#replaceOpenChatUrl} 이 쥔다 — 제거다.
   *
   * <p>⚠️ <b>이 가드는 안내용이며 원자성을 보장하지 않는다.</b> 로드 시점 {@code status} 스냅샷을 보고 쓰기는 커밋 시점 flush 라, 그
   * 사이 취소 CAS 가 커밋되면 취소된 분철에 링크가 쓰인다. 취소된 분철의 링크를 읽는 화면이 없어 무해하다고 보고 수용했다 — <b>다른 필드를 이
   * 패턴으로 얹지 마라</b>. 상태 전이처럼 결과가 남는 쓰기는 CAS 로 내려야 한다.
   *
   * <p><b>LEGACY 도 허용한다</b> — 개최·전체 수정 어디에도 flowType 가드가 없어 기존 동작과 맞춘다. C2C 전용 액션(성사 확정·진행
   * 확정)만 {@code isC2c()} 로 막는다.
   *
   * <p><b>참여자 알림은 보내지 않는다</b>(이번 범위 밖). 링크가 바뀌어도 참여자는 상세·내 참여를 다시 열어야 새 링크를 본다 — 이미 링크를
   * 복사해 간 사람에게는 안내가 닿지 않는다. 수신함 기록은 별도로 다룬다.
   */
  @Transactional
  public void updateOpenChatUrl(
      final Long hostId, final Long buncheolId, final String openChatUrl) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    buncheol.validateOpenChatUrlEditable();

    buncheolDomainService.replaceBuncheolOpenChatUrl(buncheol, openChatUrl);
  }

  /**
   * C2C 개최자 성사 확정 (RECRUITING → PAYMENT_COLLECTING, docs/46 §4.1). 신청자 전원을 일괄 입금 기한(24h)과 함께 입금
   * 대기로 전이하고, 확정 시점 개최자 계좌를 분철에 스냅샷한다(§4.7-B1). 정원 미달이어도 개최자 재량으로 확정할 수 있고(§7.1-2), deadline
   * 전 조기 확정도 허용한다(§4.7-E2) — 미달 경고·재확인은 FE 가 담당한다.
   */
  @Transactional
  public BuncheolConfirmResult confirmRecruitment(final Long hostId, final Long buncheolId) {
    final Instant now = Instant.now(clock);
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    // 입금 안내를 보낼 계좌 필수 — 개최 시점에도 검증하지만 이후 삭제됐을 수 있어 확정 시점에 재검증하고 스냅샷 소스로 쓴다.
    userDomainService.requireBankAccountRegistered(hostId);
    BankAccount hostAccount = userDomainService.getUser(hostId).getBankAccount();

    Instant paymentDueAt = now.plus(ParticipationService.C2C_PAYMENT_WINDOW);
    if (!buncheolDomainService.startCollecting(buncheolId, paymentDueAt, hostAccount, now)) {
      // 이미 확정·취소됐거나(중복 클릭·유예 취소 경합) RECRUITING 이 아님.
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);
    }

    // 전이 대상 스냅샷 — 알림 이벤트에 실어 리스너가 "커밋 시점 전이 건"만 발송하게 한다 (실행 시점 재조회는 그 사이
    // 들어온 추가 모집 참여와 중복 발송된다). 분철 CAS 선점 후라 이 목록과 아래 일괄 전이는 같은 집합이다.
    List<Long> appliedIds =
        participationDomainService.findActiveByBuncheolId(buncheolId).stream()
            .filter(participation -> participation.getStatus() == ParticipationStatus.APPLIED)
            .map(Participation::getId)
            .toList();

    int awaitingCount =
        participationDomainService.startPaymentCollecting(buncheolId, paymentDueAt, now);
    if (awaitingCount == 0) {
      // 신청자가 전부 취소된 직후 — 확정이 무의미하므로 롤백해 RECRUITING 을 유지한다.
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);
    }

    // 커밋 후 성사 확정·입금 안내 알림톡(신청자 전원, 유저 단위 합산 — docs/46 §4.7-A3)과 수신함 기록을 트리거한다.
    eventPublisher.publishEvent(new BuncheolCollectingStartedEvent(buncheolId, appliedIds));
    return new BuncheolConfirmResult(
        buncheolId, BuncheolStatus.PAYMENT_COLLECTING, paymentDueAt, awaitingCount);
  }

  /**
   * C2C 입금 수집 종료(부분 확정 — docs/46 §7.1-6). 입금 기한 경과로 미입금 슬롯이 정리된 뒤, 확정된 참여만으로 진행하겠다는 개최자
   * 선택이다. 미입금 활성 참여(입금 대기·보냈어요)가 남아 있거나 확정 참여가 없으면 CAS 조건에 막혀 실패한다 — 보냈어요 잔여는 확인/반려로 먼저
   * 정리해야 한다(§4.7-E1).
   */
  @Transactional
  public void finalizeCollected(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    if (!buncheolDomainService.confirmIfAllCollected(buncheolId, Instant.now(clock))) {
      // 성사 확정(confirmRecruitment)과 다른 단계라 전용 코드를 쓴다 (docs/53 Q-12).
      throw new BusinessException(ErrorCode.BUNCHEOL_COLLECT_FINALIZE_NOT_ALLOWED);
    }
    buncheolConfirmedFinalizer.finalizeConfirmed(buncheolId);
  }

  /**
   * 개최자의 분철 취소. 모집중·입금 수집중·인원미달 자동취소 상태에서만 가능하고, 활성 참여는 같은 트랜잭션에서 cascade 취소된다.
   *
   * <p>개최자가 <b>입금확인(CONFIRMED)한</b> 참여가 1건이라도 있으면 취소를 막는다 (docs/56 H-13). 직거래 구조라 개최자가 수령을
   * 인정한 돈은 이미 그의 계좌에 있는데 분철을 접으면 플랫폼이 환불을 강제할 수단이 없다. 인원 미달인데 일부만 입금한 경우 개최자가 스스로 접을 수
   * 없게 되는 것은 인지된 트레이드오프이고, 문의 경유 환불로 처리한다 — 그래서 에러 문구가 "환불한 뒤 고객센터로 문의" 를 안내한다.
   *
   * <p>⚠️ 판정 범위는 <b>CONFIRMED 뿐</b>이다. 참여자가 "보냈어요"(PAYMENT_SENT)만 누른 건은 실제로 돈이 갔더라도 개최자가
   * 입금확인을 누르지 않은 채 취소하면 그대로 통과한다. 마킹은 참여자의 자기 신고라 그것만으로 취소를 막으면 허위 마킹 하나로 개최자가 분철을
   * 영영 접지 못하게 되므로, 판정 기준을 개최자가 수령을 인정한 시점으로 뒀다(docs/56 §14-3 결정 그대로). 남는 리스크는 문의 경유로 처리한다.
   */
  @Transactional
  public void cancelBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    final Instant now = Instant.now(clock);

    // H-13 안내용 사전 검사. 실제 차단은 아래 CAS 서브쿼리가 원자적으로 하고, 여기서는 "왜 못 접는지 + 무엇을 해야 하는지" 를
    // 알려 주기 위해 먼저 읽는다 — CAS 만 두면 상태 위반과 구분되지 않아 BCH-050(범용 문구)으로 떨어진다.
    // 판정은 개최 목록 응답이 그대로 내려주는 것과 같은 값이다 (docs/56 S-2).
    long confirmedCount =
        BuncheolHostCancellability.requiresConfirmedCount(buncheol.getStatus())
            ? participationDomainService.countConfirmedByBuncheolId(buncheolId)
            : 0L;
    // 매핑을 switch 식으로 둬야 사유가 추가될 때 컴파일 에러로 잡힌다 — == 비교나 switch 문으로 두면 새 차단 사유가
    // 사전 검사를 통과해 목록은 "취소 불가"라 말하는데 API 는 취소를 허용하는 fail-open 이 된다.
    // BLOCKED_BY_STATUS 만 여기서 던지지 않고 CAS 에 맡긴다 — 경합에서 어느 상태로부터 전이됐는지는 CAS 만 알 수 있다.
    ErrorCode blockedCode =
        switch (BuncheolHostCancellability.of(buncheol.getStatus(), confirmedCount)) {
          case CANCELLABLE, BLOCKED_BY_STATUS -> null;
          case BLOCKED_BY_CONFIRMED_PAYMENT -> ErrorCode.BUNCHEOL_CANCEL_CONFIRMED_PAYMENT_EXISTS;
        };
    if (blockedCode != null) {
      throw new BusinessException(blockedCode);
    }

    // 모집중·입금 수집중·인원미달 자동취소 상태에서만 취소 (→ HOST_CANCELLED CAS). 마감 판정 스케줄러와 경합해도 한쪽만 성공한다.
    BuncheolStatus priorStatus = buncheolDomainService.cancelBuncheol(buncheolId, now);

    // 자동취소(CANCELLED)된 분철은 마감 스케줄러가 같은 트랜잭션에서 참여 취소·배송 스냅샷 정리·취소 알림까지 이미 끝냈다.
    // 케스케이드를 재실행하면 알림 대상 재조회(findCascadeCancelledByBuncheolId)가 그때 전이된 참여를 다시 집어
    // 취소 알림이 중복 발송되므로 여기서 종료한다. (CANCELLED 상태에선 새 참여가 생길 수 없어 잔여 활성 참여도 없다.)
    if (priorStatus == BuncheolStatus.CANCELLED) {
      return;
    }

    // 취소 확정 후 같은 트랜잭션에서 활성 참여(입금확인중·입금확인됨)를 모두 CANCELLED(BUNCHEOL_CANCELLED) 로 일괄 전이한다.
    // 입금확인된 참여의 환불은 운영자가 오프라인으로 처리한다. 알림 대상은 cascade 로 실제 전이된 참여만 재조회해 수집한다(그 사이
    // 자발취소·만료된 참여에 중복 알림이 가지 않도록).
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    List<Participation> cancelled =
        participationDomainService.findCascadeCancelledByBuncheolId(buncheolId);
    // 입금확인 시 생성된 배송 스냅샷을 정리한다 — Delivery 는 취소되지 않은 참여에만 존재해야 한다.
    deliveryDomainService.deleteByParticipationIds(
        cancelled.stream().map(Participation::getId).toList());
    cancelled.forEach(
        participation ->
            eventPublisher.publishEvent(
                new BuncheolCancelledEvent(
                    participation.getId(), BuncheolCancelReason.HOST_CANCELLED)));
  }

  private List<Long> extractDistinctMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();
    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }
    return memberIds;
  }
}
