package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
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
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.BankAccount;
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

  @Transactional
  public void holdBuncheol(
      final Long hostId, final HoldBuncheolRequest request, final List<ImageFile> images) {
    // 개최 오픈 전 — 운영이 지정한 계정(can_host)만 분철을 개최할 수 있다.
    userDomainService.requireCanHost(hostId);

    buncheolImageDomainService.validateImageCount(images.size());

    // 대표사진은 이미지 저장 순서를 바꾸지 않고 인덱스 플래그로만 지정한다 (필수 — DTO @NotNull 검증).
    buncheolImageDomainService.validateThumbnailIndex(images.size(), request.thumbnailIndex());

    // 정산 계좌가 등록된 호스트만 분철을 개최할 수 있다.
    userDomainService.requireBankAccountRegistered(hostId);

    groupDomainService.validateGroupExists(request.groupId());

    List<Long> memberIds = extractDistinctMemberIds(request.buncheolMembers());
    groupDomainService.getGroupMembersByIdsInGroup(request.groupId(), memberIds);

    // C2C 개최 오픈 전까지 개최 API 는 운영진(can_host) 전용이라 LEGACY 고정.
    // 오픈 시 자격 게이트(성인·연락처)와 함께 플로우 결정 로직으로 교체한다 (docs/46 §3-8·§7.1-8).
    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, request.toParams(FlowType.LEGACY));

    List<BuncheolMemberParams> memberParams =
        request.buncheolMembers().stream().map(BuncheolMemberRequest::toParams).toList();
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), memberParams);

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(
          new BuncheolImageUploadEvent(buncheol.getId(), images, request.thumbnailIndex()));
    }
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
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);
    }
    buncheolConfirmedFinalizer.finalizeConfirmed(buncheolId);
  }

  @Transactional
  public void cancelBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    final Instant now = Instant.now(clock);

    // 모집중·인원미달 자동취소 상태에서만 취소 (RECRUITING/CANCELLED → HOST_CANCELLED CAS). 마감 판정 스케줄러와 경합해도 한쪽만 성공한다.
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
