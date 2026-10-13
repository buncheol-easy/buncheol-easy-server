package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.BuncheolTerms;
import buncheoleasy.buncheol.domain.BuncheolTermsChange;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolMemberAddRequest;
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
import java.util.Optional;
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
  private final ParticipationBundleDomainService participationBundleDomainService;
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
    // 개최 시 자리 수 = 요청 멤버 수 (중복은 위에서, 닫힌 자리는 아래에서 거부된다).
    buncheolDomainService.validateMinHeadcountWithinSlots(
        request.minHeadcount(), request.buncheolMembers().size());

    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, request.toParams(flowType));

    List<BuncheolMemberParams> memberParams =
        request.buncheolMembers().stream().map(BuncheolMemberRequest::toParams).toList();
    validateNoClosedSlots(memberParams);
    validateCodeOnlySlotsAllowed(flowType, memberParams);
    validateCodeOnlySlotsAreFree(memberParams);
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), memberParams);

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(
          BuncheolImageUploadEvent.ofHold(buncheol.getId(), images, request.thumbnailIndex()));
    }

    return buncheol.getId();
  }

  /**
   * CLOSED 는 개최자가 신청을 받다가 「빼기」로 닫을 때만 생기는 값이다. 개최 요청으로 만들게 두면 LEGACY 에도 닫힌 자리가 생기고, 이
   * 값을 모르는 옛 서버로 롤백할 때 "닫힌 자리는 빼기 기능이 쓰인 뒤에만 있다" 는 대비 전제가 깨진다.
   */
  private void validateNoClosedSlots(final List<BuncheolMemberParams> memberParams) {
    if (memberParams.stream().anyMatch(param -> param.accessType().isClosed())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }

  /** 코드 발급 API 가 관리자 전용이라, C2C 에 코드 슬롯을 만들면 아무도 발급할 수 없는 영구 잠긴 슬롯이 된다. */
  private void validateCodeOnlySlotsAllowed(
      final FlowType flowType, final List<BuncheolMemberParams> memberParams) {
    if (flowType == FlowType.LEGACY) {
      return;
    }
    if (memberParams.stream().anyMatch(param -> param.accessType().requiresCode())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_C2C_CODE_MEMBER_NOT_ALLOWED);
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

  /**
   * 분철 수정 — 모집중·마감 전에만. 제목·설명·사진·오픈채팅 링크는 신청자가 있어도 바꿀 수 있고, 거래 조건(마감·최소 인원·배송비·구매처)은
   * <b>활성 신청이 하나도 없는 C2C 분철</b>에서만 바꾼다. 조건 칸은 지금 값과 다를 때만 바뀐 것으로 본다 — 조건 칸을 보내지 않거나 지금 값을
   * 그대로 보낸 요청은 이전과 같다.
   *
   * <p>거래 조건 쓰기는 더티체킹이 아니라 「활성 신청 없음」을 함께 보는 조건부 UPDATE 다. 앞의 판정({@link
   * Buncheol#termsEditability})은 안내용이고, 판정 뒤 들어온 신청은 그 UPDATE 가 막아 409 로 전부 롤백된다(제목·사진 변경 포함).
   *
   * <p>락 순서는 분철 행(X) → 자리 행(S, 최소 인원을 바꿀 때) → 참여 행(UPDATE 의 NOT EXISTS) 으로, C2C 신청·자리 빼기와 같은
   * 방향이다.
   */
  @Transactional
  public void modifyBuncheol(
      final Long hostId,
      final Long buncheolId,
      final BuncheolModifyRequest request,
      final List<ImageFile> images) {
    final Instant now = Instant.now(clock);
    // 🔴 잠금 조회가 이 트랜잭션의 첫 분철 조회여야 한다 — lockForSlotEdit 주석과 같은 함정. 신청과 분철 행에서 한 줄로 서고, 조건
    // 칸은 잠근 최신 값과 비교한다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(buncheolId);
    buncheol.validateOwner(hostId);
    buncheol.validateRecruiting(now);

    Optional<BuncheolTerms> newTerms = resolveTermsChange(buncheol, request.toTermsChange(), now);

    buncheolImageDomainService.validateModifyImageCount(
        buncheolId, request.keepImageIds(), images.size());
    buncheolImageDomainService.validateThumbnailSelection(
        request.keepImageIds(), images.size(), request.thumbnailImageId(), request.thumbnailIndex());

    buncheolDomainService.updateBuncheolContent(buncheol, request.title(), request.description());
    buncheolDomainService.updateBuncheolOpenChatUrl(buncheol, request.openChatUrl());

    // 조건 쓰기는 제목·설명 변경 뒤에 둔다 — 이 UPDATE 가 영속성 컨텍스트를 먼저 flush 하고 비우므로, 앞의 더티체킹 변경이 함께
    // 나가고 이후 엔티티 flush 가 조건 칸을 되쓰지 않는다. 실패하면 예외로 전부 롤백되고 아래 이미지 벌크도 돌지 않는다.
    if (newTerms.isPresent()
        && !buncheolDomainService.changeTermsIfNoApplicant(buncheolId, newTerms.get(), now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT);
    }

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
          BuncheolImageUploadEvent.ofModify(buncheolId, images, request.thumbnailIndex()));
    }
  }

  /**
   * 요청이 거래 조건을 실제로 바꾸면 수정 가능 여부를 판정하고 바뀐 칸을 검증해 새 조건을 돌려준다. 바뀐 칸이 없으면 비어 있다 — 그때는
   * 신청자 여부를 묻지 않는다(신청자가 있어도 제목·사진 수정은 열려 있다).
   */
  private Optional<BuncheolTerms> resolveTermsChange(
      final Buncheol buncheol, final BuncheolTermsChange change, final Instant now) {
    if (!buncheol.changesTerms(change)) {
      return Optional.empty();
    }
    // 안내용 사전 검사 — 상세 응답의 termsEditable 과 같은 판정이다. 최종 차단은 조건부 UPDATE 가 한다.
    // 분철 행을 잠근 뒤라 신청이 새로 커밋될 수 없어, 여기서 본 「신청자 없음」이 그 UPDATE 까지 유지된다.
    boolean hasActiveParticipation =
        !participationDomainService.findActiveByBuncheolId(buncheol.getId()).isEmpty();
    // switch 식이라 판정 값이 늘면 컴파일 에러로 매핑을 강제한다(cancelBuncheol 과 같은 이유).
    ErrorCode blockedCode =
        switch (buncheol.termsEditability(now, hasActiveParticipation)) {
          case EDITABLE -> null;
          case BLOCKED_BY_STATUS -> ErrorCode.BUNCHEOL_NOT_RECRUITING;
          case BLOCKED_BY_FLOW -> ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED;
          case BLOCKED_BY_APPLICANT -> ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT;
        };
    if (blockedCode != null) {
      throw new BusinessException(blockedCode);
    }

    BuncheolTerms terms = buncheol.applyTermsChange(change, now);
    if (terms.minHeadcount() != buncheol.getMinHeadcount()) {
      // 닫힌 자리는 신청을 받지 않으므로 세지 않는다 — 자리 빼기의 최소 인원 하향과 같은 수다. 분철 잠금 뒤 잠금 읽기라 최신이다.
      int openSlotCount =
          (int)
              buncheolMemberDomainService.findAllByBuncheolIdForShare(buncheol.getId()).stream()
                  .filter(slot -> !slot.isClosed())
                  .count();
      buncheolDomainService.validateMinHeadcountWithinSlots(terms.minHeadcount(), openSlotCount);
    }
    return Optional.of(terms);
  }

  /**
   * 오픈채팅 링크만 수정한다. 전체 수정({@link #modifyBuncheol})은 모집중·마감 전으로 묶여 있는데, 링크는 참여자가 입금하며 문의하는
   * 구간에서 오히려 더 필요하다. 그 구간을 열어 주되 가격·멤버 보호 가드는 전체 수정 경로에 그대로 둔다.
   *
   * <p>공백 해석은 {@link Buncheol#replaceOpenChatUrl} 이 쥔다 — 제거다.
   *
   * <p>⚠️ <b>이 가드는 안내용이며 원자성을 보장하지 않는다.</b> 로드 시점 {@code status} 스냅샷을 보고 쓰기는 커밋 시점 flush 라, 그
   * 사이 취소 CAS 가 커밋되면 취소된 분철에 링크가 쓰인다. <b>취소된 분철의 링크를 읽는 경로는 있다</b> — {@code
   * MyParticipationQueryService}·{@code ParticipationDetailQueryService} 는 분철 상태를 보지 않는다. 그런데도 수용한 것은
   * <b>노출 대상이 넓어지지 않기 때문</b>이다: ① 두 경로 모두 <b>참여자 본인</b>에게만 열려 있고, 개최자가 저장을 누른 시점에 이미 그들에게
   * 보여줄 작정이던 값이다(기존 값이 있던 건이면 경합이 없어도 같은 참여자가 같은 자리에서 계속 본다) ② 이 경로는 이벤트를 발행하지 않아 잘못
   * 쓰인 값이 능동적으로 퍼지지 않는다. ⚠️ 다만 화면이 취소 건을 전부 가려 주지는 않는다 — 클라 {@code BidHistoryContent} 의 카드
   * 경로는 숨기지만 결제 시트 경로에는 취소 게이트가 없다. ⚠️ 그리고 취소 뒤에는 개최자가 스스로 되돌릴 수 없다({@link
   * Buncheol#validateOpenChatUrlEditable} 이 CANCELLED·HOST_CANCELLED 를 막는다). <b>다른 필드를 이 패턴으로 얹지
   * 마라</b>. 상태 전이처럼 결과가 남는 쓰기는 CAS 로 내려야 한다.
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
    // 신청 구간의 묶음은 기한 없이 열려 있다 — 참여 행과 같은 기한을 묶음에도 채운다. 비워 두면 나중에 붙을
    // 개최자 「제외」 기한 가드가 fail-open 된다(기한 안인데도 참여자를 뺄 수 있게 된다 — docs/71 §8-1).
    participationBundleDomainService.assignDueAtByBuncheolId(buncheolId, paymentDueAt, now);

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
    // cascade 는 참여를 한 번의 UPDATE 로 전이시켜 어느 묶음이 비었는지 개별로 알 수 없다 — 같은 CAS 조건을
    // 분철 범위로 넓혀 일괄 판정한다. 안 닫으면 취소된 분철에 활성 묶음이 영구히 남는다.
    participationBundleDomainService.closeEmptyByBuncheolId(buncheolId, now);
    List<Long> cancelledIds =
        participationDomainService.findCascadeCancelledByBuncheolId(buncheolId).stream()
            .map(Participation::getId)
            .sorted()
            .toList();
    // 입금확인 시 생성된 배송 스냅샷을 정리한다 — Delivery 는 취소되지 않은 참여에만 존재해야 한다.
    deliveryDomainService.deleteByParticipationIds(cancelledIds);
    // 분철 단위로 1번 발행한다 — 참여마다 발행하면 다슬롯 참여자가 취소 알림을 슬롯 수만큼 받는다.
    if (!cancelledIds.isEmpty()) {
      eventPublisher.publishEvent(
          new BuncheolCancelledEvent(
              buncheolId, cancelledIds, BuncheolCancelReason.HOST_CANCELLED));
    }
  }

  /**
   * C2C 개최자가 신청을 받는 동안(모집중·입금 수집중) 빈 자리를 분철에서 뺀다 — 다른 곳에서 먼저 팔린 멤버의 이중 판매를 막는 용도다.
   *
   * <ul>
   *   <li>참여·참여 코드 기록이 하나도 없는 자리는 행을 지운다 — 상세·목록·찜·멤버 검색에서 처음부터 없던 멤버처럼 사라진다.
   *   <li>기록(취소 등)만 남은 자리는 FK 때문에 지울 수 없어 {@code CLOSED} 로 닫는다 — 상세에 「마감」으로 남고 신청은 막힌다.
   *   <li>이미 닫힌 자리는 그대로 둔다(멱등). 활성 신청자가 있는 자리는 BCH-118.
   * </ul>
   *
   * <p>「남은 자리」는 <b>열린 자리</b>(닫히지 않은 자리)다. 마지막 열린 자리는 뺄 수 없다(BCH-120) — 신청을 받을 자리가 0 인
   * 분철은 어디에서도 전제하지 않는다. 단 그 자리에 신청자가 있으면 BCH-118 이 먼저다 — 신청자가 있는 분철에 「분철을 취소해
   * 주세요」를 권하면 안 된다.
   *
   * <p>모집중에 빼서 열린 자리 수가 최소 인원보다 작아지면 최소 인원을 열린 자리 수로 낮춘다. 그대로 두면 C2C 는 마감 정각에 인원
   * 미달로 즉시 취소되어 신청자까지 전원 취소된다.
   *
   * <p>락 순서는 분철 행(X) → 자리 행(X) → 참여·참여 코드 행(S, DELETE·닫기 CAS 의 NOT EXISTS) 이다. C2C 신청(분철 X → 자리
   * S → 참여 INSERT)·개최자 취소 CAS 와 같은 「분철 → 자리 → 참여」 방향이라 신청과는 분철 행에서 한 줄로 선다. 입금확인(참여 →
   * 분철)과는 역방향이지만, 겹치려면 개최자가 같은 자리의 입금확인과 빼기를 동시에 눌러야 하고 실패 모드는 기존과 같은 데드락
   * 롤백이다(ParticipationService#publishFullIfAllSlotsApplied).
   */
  @Transactional
  public void removeMember(final Long hostId, final Long buncheolId, final Long buncheolMemberId) {
    final Instant now = Instant.now(clock);
    lockForSlotEdit(hostId, buncheolId, now);

    // 잠금 조회라 분철 락을 기다리는 동안 커밋된 다른 빼기까지 센다 — 스냅샷으로 세면 두 탭에서 동시에 빼기를 눌렀을 때
    // 둘 다 "2자리 남음" 을 보고 자리가 0 이 될 수 있다.
    List<BuncheolMember> slots =
        buncheolMemberDomainService.findAllByBuncheolIdForUpdate(buncheolId);
    BuncheolMember target =
        slots.stream()
            .filter(slot -> slot.getId().equals(buncheolMemberId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND));
    // 화면이 낡았거나 두 번 누른 경우 — 이미 닫혔으니 할 일이 없다. 열린 자리 수도 그대로라 최소 인원도 건드리지 않는다.
    if (target.isClosed()) {
      return;
    }
    // 닫힌 자리는 신청을 받지 않으므로 「남은 자리」로 세지 않는다. 마지막 자리 판정과 최소 인원 하향이 같은 수를 쓴다.
    int openSlotCount = (int) slots.stream().filter(slot -> !slot.isClosed()).count();
    if (openSlotCount <= 1) {
      // 판정은 위 잠금 조회로 끝났고, 여기서 다시 읽는 것은 어느 안내를 줄지뿐이다(지우지 않으므로 원자성과 무관).
      throw new BusinessException(
          participationDomainService.hasActiveParticipationOnSlot(buncheolMemberId)
              ? ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT
              : ErrorCode.BUNCHEOL_MEMBER_REMOVE_LAST);
    }

    // 기록이 하나도 없으면 행을 지우고, 기록(취소 등)만 남아 FK 로 못 지우면 닫는다. 둘 다 원자적 CAS 라 그 사이 들어온
    // 신청은 닫기 CAS 의 NOT EXISTS(활성 참여)가 막는다 — 둘 다 실패하면 활성 신청자가 있는 것이다.
    if (!buncheolMemberDomainService.removeIfUnreferenced(buncheolMemberId, buncheolId)
        && !buncheolMemberDomainService.closeIfUnoccupied(buncheolMemberId, buncheolId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT);
    }

    // 모집중 여부는 UPDATE 조건이 판정한다(입금 수집중이면 0행). 위 DELETE·닫기가 영속성 컨텍스트를 비웠으므로 엔티티
    // 더티체킹이 아니라 UPDATE 로 쓴다.
    buncheolDomainService.lowerMinHeadcountToSlotCount(buncheolId, openSlotCount - 1, now);
  }

  /**
   * C2C 개최자가 신청을 받는 동안 그 분철 그룹의 멤버를 자리로 (다시) 넣는다 — 실수로 뺀 자리를 되살리는 경로다. 개최와 같은
   * 검사(그룹 소속·중복 금지·가격 규칙)를 하고, 선착순(OPEN) 자리로 만든다. 최소 인원은 올리지 않는다.
   *
   * <p>같은 멤버의 닫힌({@code CLOSED}) 자리가 있으면 새로 만들지 않고 <b>그 자리를 다시 연다</b>(가격은 새로 받은 값) — 기록이 FK 로
   * 붙어 있어 지울 수 없고 (분철, 멤버) 유니크라 새 행도 만들 수 없다. 열린 자리가 있으면 BCH-121.
   *
   * @return 새로 만들었거나 다시 연 자리 id
   */
  @Transactional
  public Long addMember(
      final Long hostId, final Long buncheolId, final BuncheolMemberAddRequest request) {
    final Instant now = Instant.now(clock);
    Buncheol buncheol = lockForSlotEdit(hostId, buncheolId, now);

    groupDomainService.getGroupMembersByIdsInGroup(
        buncheol.getGroupId(), List.of(request.memberId()));
    // 사전 조회는 어느 경로로 갈지만 정한다. 경합은 분철 락이 직렬화하고, 다시 열기는 CAS(닫힌 자리만)가, 새 자리는 유니크
    // 인덱스가 마지막 선이다.
    Optional<BuncheolMember> existing =
        buncheolMemberDomainService.findInBuncheol(buncheolId, request.memberId());
    if (existing.isPresent()) {
      Long slotId = existing.get().getId();
      if (existing.get().isClosed()
          && buncheolMemberDomainService.reopenClosedSlot(slotId, buncheolId, request.price())) {
        return slotId;
      }
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
    }
    return buncheolMemberDomainService
        .addOpenSlot(buncheolId, request.memberId(), request.price())
        .getId();
  }

  /**
   * C2C 개최자가 신청을 받는 동안(모집중·입금 수집중) 빈 자리 하나의 가격을 바꾼다. 빼기와 같은 규칙 — 그 자리에 활성 신청자가 있으면
   * 바꾸지 않는다(그 사람이 보고 신청한 가격이다). 다른 자리의 신청자는 막지 않는다.
   *
   * <p>닫힌 자리는 바꾸지 않는다 — 다시 넣기({@link #addMember})가 가격을 받아 연다. 판정과 쓰기는 조건부 UPDATE 하나이고, 락 순서는
   * 분철 행(X) → 자리 행(X, UPDATE) → 참여 행(S, NOT EXISTS) 으로 빼기와 같다.
   */
  @Transactional
  public void changeMemberPrice(
      final Long hostId, final Long buncheolId, final Long buncheolMemberId, final long price) {
    final Instant now = Instant.now(clock);
    lockForSlotEdit(hostId, buncheolId, now);

    if (!buncheolMemberDomainService.changePriceIfUnoccupied(buncheolMemberId, buncheolId, price)) {
      // 판정은 위 UPDATE 로 끝났고, 여기서 읽는 것은 어느 안내를 줄지뿐이다 — 그 분철의 자리가 아니면 빼기와 같은 404.
      buncheolMemberDomainService.getBuncheolMember(buncheolMemberId, buncheolId);
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_PRICE_CHANGE_NOT_ALLOWED);
    }
  }

  /**
   * 자리 빼기·넣기·가격 바꾸기 공통 진입 검사. 분철 행을 잠가 C2C 신청(분철 락 → 자리 조회)과 한 줄로 세운다.
   *
   * <p>🔴 이 잠금 조회가 트랜잭션의 <b>첫 분철 조회</b>여야 한다. 앞에서 {@code getBuncheol} 을 부르면 영속성 컨텍스트가 락 이전에
   * 읽은 인스턴스를 그대로 돌려줘 상태 판정이 낡는다(ParticipationService#participateC2c 주석과 같은 함정).
   */
  private Buncheol lockForSlotEdit(final Long hostId, final Long buncheolId, final Instant now) {
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(buncheolId);
    buncheol.validateOwner(hostId);
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
    // 참여 가드·상세의 공석 표시와 같은 술어 — 신청을 받는 구간에서만 자리를 바꾼다.
    if (!buncheol.acceptsNewParticipation(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    return buncheol;
  }

  private List<Long> extractDistinctMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();
    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }
    return memberIds;
  }
}
