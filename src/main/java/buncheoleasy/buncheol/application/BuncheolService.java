package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.BuncheolTerms;
import buncheoleasy.buncheol.domain.BuncheolTermsChange;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingDomainService;
import buncheoleasy.buncheol.dto.request.BuncheolConfirmRequest;
import buncheoleasy.buncheol.dto.request.BuncheolConfirmRequest.CancelParticipation;
import buncheoleasy.buncheol.dto.request.BuncheolMemberAddRequest;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.buncheol.dto.request.HoldStaffBuncheolRequest;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.C2cHostQualification;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolConfirmer buncheolConfirmer;
  private final PaymentStarter paymentStarter;
  private final BuncheolImageDomainService buncheolImageDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final DeliveryDomainService deliveryDomainService;
  private final GroupDomainService groupDomainService;
  private final UserDomainService userDomainService;
  private final StaffBuncheolSettingDomainService staffBuncheolSettingDomainService;
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
    return createBuncheolWithMembers(
            hostId,
            request.toParams(flowType),
            request.thumbnailIndex(),
            request.buncheolMembers(),
            images)
        .getId();
  }

  /**
   * 운영진 분철을 개최한다. 언제나 운영진 분철(LEGACY)이고, 예약 오픈 시각이 있으면 같은 트랜잭션에서 설정 행을 함께 저장한다.
   *
   * <p>🔴 설정 저장을 이벤트 리스너나 별도 트랜잭션으로 빼지 않는다. 분철이 먼저 커밋되면 오픈 시각 없이 열린 분철이 잠깐 보이고, 설정
   * 저장이 실패하면 그대로 남는다. 한 트랜잭션이면 커밋 전에는 아무도 새 분철을 볼 수 없어 "분철은 있는데 설정은 없는" 순간이 없다.
   *
   * <p>자격 판정은 회원 개최의 운영진 경로({@link #resolveHostFlowType} 의 LEGACY)와 같다 — {@link #getHostingEligibility} 가
   * 운영진을 LEGACY 기준으로 판정하는 것과도 맞는다.
   *
   * @return 생성된 분철 id
   */
  @Transactional
  public Long holdStaffBuncheol(
      final Long hostId, final HoldStaffBuncheolRequest request, final List<ImageFile> images) {
    if (!userDomainService.canHost(hostId)) {
      throw new BusinessException(ErrorCode.USER_CANNOT_HOST);
    }
    final Instant now = Instant.now(clock);
    if (request.opensAt() != null) {
      staffBuncheolSettingDomainService.validateOpensAt(request.opensAt(), request.deadline(), now);
    }

    Buncheol buncheol =
        createBuncheolWithMembers(
            hostId,
            request.toParams(),
            request.thumbnailIndex(),
            request.buncheolMembers(),
            images);

    if (request.opensAt() != null) {
      staffBuncheolSettingDomainService.create(
          buncheol.getId(), request.opensAt(), request.deadline(), now);
    }
    return buncheol.getId();
  }

  /** 회원 개최와 운영진 개최가 함께 쓰는 본문: 자리 검증 → 분철 생성 → 자리 생성 → 이미지 업로드 이벤트. */
  private Buncheol createBuncheolWithMembers(
      final Long hostId,
      final BuncheolParams params,
      final Integer thumbnailIndex,
      final List<BuncheolMemberRequest> members,
      final List<ImageFile> images) {
    buncheolImageDomainService.validateImageCount(images.size());

    // 대표사진은 이미지 저장 순서를 바꾸지 않고 인덱스 플래그로만 지정한다 (필수 — DTO @NotNull 검증).
    buncheolImageDomainService.validateThumbnailIndex(images.size(), thumbnailIndex);

    // 정산 계좌가 등록된 호스트만 분철을 개최할 수 있다 (LEGACY·C2C 공통 — C2C 는 입금 안내 계좌 스냅샷의 원천).
    userDomainService.requireBankAccountRegistered(hostId);

    groupDomainService.validateGroupExists(params.groupId());

    List<Long> memberIds = extractDistinctMemberIds(members);
    groupDomainService.getGroupMembersByIdsInGroup(params.groupId(), memberIds);
    // 개최 시 자리 수 = 요청 멤버 수 (중복은 위에서, 닫힌 자리는 아래에서 거부된다).
    buncheolDomainService.validateMinHeadcountWithinSlots(params.minHeadcount(), members.size());

    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, params);

    List<BuncheolMemberParams> memberParams =
        members.stream().map(BuncheolMemberRequest::toParams).toList();
    validateNoClosedSlots(memberParams);
    validateCodeOnlySlotsAllowed(params.flowType(), memberParams);
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), memberParams);

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(
          BuncheolImageUploadEvent.ofHold(buncheol.getId(), images, thumbnailIndex));
    }

    return buncheol;
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
   * <p><b>LEGACY 도 허용한다</b> — 개최·전체 수정 어디에도 flowType 가드가 없어 기존 동작과 맞춘다. C2C 전용 액션(진행 확정
   * 등)만 플로우로 막는다.
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
   * 회원 개최 진행 확정 (PAYMENT_COLLECTING → CONFIRMED). 모든 참여가 입금 확인됐거나, 입금 기한이 지났고 확인된 참여가 있을 때만
   * 된다({@link ConfirmCheck}). 입금이 확인되지 않은 참여(입금 대기·「보냈어요」)는 같은 트랜잭션에서 빠지고({@code
   * PAYMENT_NOT_CONFIRMED}), 확인된 묶음마다 배송이 생긴다 — 운송장은 이 뒤에만 넣을 수 있다.
   *
   * <p>{@code request} 의 빠질 참여 목록은 개최자가 확인 창에서 본 것이다. 서버가 잠금 아래에서 본 미확인 (id, 상태) 집합과 다르면
   * 409(BCH-127)로 막는다 — 그 사이 누가 「보냈어요」를 누르거나 다른 탭에서 입금 확인했으면 개최자가 강조 없이 본 사람을 빼게 된다. 목록은
   * 대조에만 쓰고 취소 대상은 분철 범위로 고른다(남의 참여 id 를 넣어도 대조 실패로 끝난다).
   *
   * <p>🔴 READ COMMITTED 다({@link BuncheolConfirmer} 계약). 진행확정 알림 대상을 잠금 뒤 일반 조회로 읽는다. 지금은 잠금 앞에
   * 일반 조회가 없어 REPEATABLE READ 여도 같지만, 앞에 조회가 하나라도 끼면 그 스냅샷이 기다리는 사이 커밋된 입금 확인을 빠뜨리므로
   * #245 와 같이 고정해 둔다. 회원 개최에서 확정 수를 바꾸는 쓰기(묶음 입금 확인)는 모두 분철 행을 먼저 잡으므로 READ COMMITTED
   * 에서도 판정이 흔들리지 않고, 참여 잠금에 간격 잠금이 빠져도 새 참여 INSERT 는 분철 행에서 막힌다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void confirmBuncheol(
      final Long hostId, final Long buncheolId, final BuncheolConfirmRequest request) {
    // 분철 행이 첫 잠금이다(BuncheolDomainService#getBuncheolForUpdate).
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(buncheolId);
    buncheol.validateOwner(hostId);
    if (buncheol.isStaff()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }
    final Instant now = Instant.now(clock);

    // 바로 아래에서 바꿀 행이라 배타 잠금으로 읽는다.
    List<Participation> active =
        participationDomainService.findActiveByBuncheolIdForUpdate(buncheolId);
    long confirmedCount =
        active.stream().filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED).count();
    Set<CancelParticipation> unconfirmed =
        active.stream()
            .filter(p -> ParticipationStatus.payableStatuses().contains(p.getStatus()))
            .map(p -> new CancelParticipation(p.getId(), p.getStatus()))
            .collect(Collectors.toSet());

    ConfirmCheck check =
        ConfirmCheck.of(
            buncheol.getStatus(),
            confirmedCount,
            unconfirmed.size(),
            buncheol.getPaymentDueAt(),
            now);
    if (check == null || !check.isConfirmable()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);
    }
    // 순서와 무관하게 집합으로 대조한다 — 화면이 정렬을 보장하지 않는다.
    if (!unconfirmed.equals(Set.copyOf(request.cancelParticipations()))) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_LIST_CHANGED);
    }

    buncheolConfirmer.confirm(buncheolId, active, now);
  }

  /**
   * 개최자의 분철 취소. 모집중·인원미달 자동취소 상태에서만 가능하고, 모집중이면 활성 참여가 같은 트랜잭션에서 cascade 취소된다.
   *
   * <p>입금 수집중(회원 개최)에는 개최자 혼자 접지 않는다 — 참여자에게 계좌가 공개된 뒤라 입금 확인 건수와 무관하게 BCH-126 으로
   * 「운영진에게 알려 주세요」를 안내하고, 운영자가 실제 입금·환불 계획을 확인한 뒤 어드민에서 파기한다.
   */
  @Transactional
  public void cancelBuncheol(final Long hostId, final Long buncheolId) {
    // 분철 행이 첫 잠금이다(BuncheolDomainService#getBuncheolForUpdate). 운영진 분철도 지나지만 원래 첫 잠금이 같은 행의
    // 취소 CAS 라 잠그는 시점만 앞으로 온다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(buncheolId);
    buncheol.validateOwner(hostId);
    final Instant now = Instant.now(clock);

    // 잠근 상태로 가른다 — 개최 목록이 내려주는 것과 같은 판정이다(docs/56 S-2). 매핑을 switch 식으로 둬야 사유가 추가될 때
    // 컴파일 에러로 잡힌다. BLOCKED_BY_STATUS 는 여기서 던지지 않고 아래 CAS 에 맡긴다(BCH-050).
    ErrorCode blockedCode =
        switch (BuncheolHostCancellability.of(buncheol.getStatus())) {
          case CANCELLABLE, BLOCKED_BY_STATUS -> null;
          case NEEDS_STAFF -> ErrorCode.BUNCHEOL_CANCEL_NEEDS_STAFF;
        };
    if (blockedCode != null) {
      throw new BusinessException(blockedCode);
    }

    // 모집중·인원미달 자동취소 상태에서만 취소 (→ HOST_CANCELLED CAS).
    BuncheolStatus priorStatus = buncheolDomainService.cancelBuncheol(buncheolId, now);

    // 자동취소(CANCELLED)된 분철은 마감 스케줄러가 같은 트랜잭션에서 참여 취소·배송 스냅샷 정리·취소 알림까지 이미 끝냈다.
    // 케스케이드를 재실행하면 알림 대상 재조회(findCascadeCancelledByBuncheolId)가 그때 전이된 참여를 다시 집어
    // 취소 알림이 중복 발송되므로 여기서 종료한다. (CANCELLED 상태에선 새 참여가 생길 수 없어 잔여 활성 참여도 없다.)
    if (priorStatus == BuncheolStatus.CANCELLED) {
      return;
    }
    cancelledFinalizer().finalizeCancelled(buncheolId, now, BuncheolCancelReason.HOST_CANCELLED);
  }

  private BuncheolCancelledFinalizer cancelledFinalizer() {
    return new BuncheolCancelledFinalizer(
        participationDomainService,
        participationBundleDomainService,
        deliveryDomainService,
        eventPublisher);
  }

  /**
   * C2C 개최자가 신청을 받는 동안(모집중·마감 전) 빈 자리를 분철에서 뺀다 — 다른 곳에서 먼저 팔린 멤버의 이중 판매를 막는 용도다.
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
   * 미달로 즉시 취소되어 신청자까지 전원 취소된다. 빼고 나니 남은 열린 자리가 모두 신청됐으면 같은 트랜잭션에서 입금 단계를 시작한다
   * ({@link PaymentStarter#startIfFull}, 신청 경로와 같은 판정).
   *
   * <p>락 순서는 분철 행(X) → 자리 행(X) → 참여·참여 코드 행(S, DELETE·닫기 CAS 의 NOT EXISTS) 이다. C2C 신청(분철 X → 자리
   * S → 참여 INSERT)·개최자 취소와 같은 「분철 → 자리 → 참여」 방향이라 분철 행에서 한 줄로 선다.
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

    // 모집중 여부는 UPDATE 조건도 함께 본다. 위 DELETE·닫기가 영속성 컨텍스트를 비웠으므로 엔티티 더티체킹이 아니라 UPDATE 로
    // 쓴다.
    buncheolDomainService.lowerMinHeadcountToSlotCount(buncheolId, openSlotCount - 1, now);

    // 빼고 나니 남은 열린 자리가 모두 신청됐으면 이 요청 안에서 입금 단계가 시작된다. 방금 낮춘 최소 인원을 봐야 하므로
    // 잠금 읽기로 다시 읽는다(위 UPDATE 들이 영속성 컨텍스트를 비웠다 — 앞의 인스턴스는 낡았다).
    paymentStarter.startIfFull(buncheolDomainService.getLatestBuncheolForUpdate(buncheolId), now);
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
   * C2C 개최자가 <b>모집중·마감 전</b>에 빈 자리 하나의 가격을 바꾼다. 빼기와 같은 규칙 — 그 자리에 활성 신청자가 있으면 바꾸지
   * 않는다(그 사람이 보고 신청한 가격이다). 다른 자리의 신청자는 막지 않는다.
   *
   * <p>빼기·넣기와 같이 입금 단계가 시작되면 열지 않는다({@link #lockForSlotEdit}). 아래 모집중 검사는 마감 뒤 판정 전 구간도
   * 막는다.
   *
   * <p>닫힌 자리는 바꾸지 않는다 — 다시 넣기({@link #addMember})가 가격을 받아 연다. 판정과 쓰기는 조건부 UPDATE 하나이고, 락 순서는
   * 분철 행(X) → 자리 행(X, UPDATE) → 참여 행(S, NOT EXISTS) 으로 빼기와 같다.
   */
  @Transactional
  public void changeMemberPrice(
      final Long hostId, final Long buncheolId, final Long buncheolMemberId, final long price) {
    final Instant now = Instant.now(clock);
    Buncheol buncheol = lockForSlotEdit(hostId, buncheolId, now);
    buncheol.validateRecruiting(now);

    if (!buncheolMemberDomainService.changePriceIfUnoccupied(buncheolMemberId, buncheolId, price)) {
      // 판정은 위 UPDATE 로 끝났고, 여기서 읽는 것은 어느 안내를 줄지뿐이다 — 그 분철의 자리가 아니면 빼기와 같은 404.
      buncheolMemberDomainService.getBuncheolMember(buncheolMemberId, buncheolId);
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_PRICE_CHANGE_NOT_ALLOWED);
    }
  }

  /**
   * 자리 빼기·넣기·가격 바꾸기 공통 진입 검사. 분철 행을 잠가 C2C 신청(분철 락 → 자리 조회)과 한 줄로 세운다. 모집중·마감 전에만
   * 연다 — 입금 단계가 시작되면 자리가 거래 조건이 된다.
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
