package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.image.BuncheolImageUploadEvent;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
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

    // 정산 계좌가 등록된 호스트만 분철을 개최할 수 있다.
    userDomainService.requireBankAccountRegistered(hostId);

    groupDomainService.validateGroupExists(request.groupId());

    List<Long> memberIds = extractDistinctMemberIds(request.buncheolMembers());
    groupDomainService.getGroupMembersByIdsInGroup(request.groupId(), memberIds);

    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, request.toParams());

    List<BuncheolMemberParams> memberParams =
        request.buncheolMembers().stream().map(BuncheolMemberRequest::toParams).toList();
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), memberParams);

    if (!images.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolImageUploadEvent(buncheol.getId(), images));
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

    buncheolDomainService.updateBuncheolContent(buncheol, request.title(), request.description());

    buncheolImageDomainService.deleteImagesExcluding(buncheolId, request.keepImageIds());
    if (!images.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolImageUploadEvent(buncheolId, images));
    }
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
