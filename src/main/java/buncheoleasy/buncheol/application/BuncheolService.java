package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
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
  private final GroupDomainService groupDomainService;
  private final UserDomainService userDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void holdBuncheol(
      final Long hostId, final HoldBuncheolRequest request, final List<ImageFile> images) {
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

    buncheolImageDomainService.validateImageCount(request.keepImageIds().size() + images.size());

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
    // TODO: 추후 참여자 존재 시 패널티 부과
    final Instant now = Instant.now(clock);
    // 취소될 활성 참여를 상태 전이 전에 선조회(알림 수신 대상). cancelActiveByBuncheolId 가 취소하는 ACTIVE_BID 집합과 동일하다.
    // 선조회~취소 사이 신규 입찰/자가취소가 끼면 알림 대상과 실제 취소 집합이 미세하게 어긋날 수 있으나(host 단일 액터·짧은 tx) 영향은 경미하다.
    List<Participation> cancelledParticipations =
        participationDomainService.findBiddingByBuncheolId(buncheolId);
    buncheolDomainService.cancelBuncheol(buncheol);
    // 분철과 같은 트랜잭션 안에서 활성 참여를 모두 자동 CANCELLED 로 전이.
    // bulk UPDATE 의 flushAutomatically=true 가 위의 Buncheol dirty 변경도 함께 flush 한다.
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    cancelledParticipations.forEach(
        participation ->
            eventPublisher.publishEvent(new BuncheolCancelledEvent(participation.getId())));
  }

  @Transactional
  public void closeBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    buncheolDomainService.closeBuncheol(buncheol);
    // 마감 직후 같은 트랜잭션에서 낙찰자 선정: 멤버별 1순위만 AWAITING_PAYMENT(입금 대기), 2순위 이하는 ACTIVE_BID 유지(차순위 승계 후보).
    // Buncheol RECRUITING→CLOSED 가드 덕에 정확히 1회만 실행된다 (자동 마감 스케줄러도 동일 경로 재사용).
    List<Participation> winners =
        participationDomainService.selectWinners(buncheolId, Instant.now(clock));
    winners.forEach(winner -> eventPublisher.publishEvent(new ParticipationWonEvent(winner.getId())));
  }

  private List<Long> extractDistinctMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();
    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }
    return memberIds;
  }
}
