package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
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
    buncheolDomainService.cancelBuncheol(buncheol);
  }

  @Transactional
  public void closeBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    buncheolDomainService.closeBuncheol(buncheol);
    // TODO: 마감 시 등수 산정(Participation.closedRank) + 1순위 낙찰자 결제 요청
    //   (ACTIVE_BID → AWAITING_PAYMENT) 후속 처리 미구현. 해당 로직 추가 시 본 메서드에서 함께
    //   호출해야 한다 — 자동 마감 스케줄러도 같은 후속 처리를 트리거해야 하므로 도메인 서비스 레벨로 추출 권장.
  }

  private List<Long> extractDistinctMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();
    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }
    return memberIds;
  }
}
