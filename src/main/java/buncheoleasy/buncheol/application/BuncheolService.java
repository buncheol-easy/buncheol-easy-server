package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolModificationPolicy;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolModificationPolicy buncheolModificationPolicy;
  private final BuncheolImageDomainService buncheolImageDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationRepository participationRepository;
  private final GroupDomainService groupDomainService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void holdBuncheol(
      final Long hostId, final HoldBuncheolRequest request, final List<ImageFile> images) {
    buncheolImageDomainService.validateImageCount(images.size());

    groupDomainService.validateGroupExists(request.groupId());

    List<BuncheolMemberParams> buncheolMemberParams =
        toBuncheolMemberParams(request.groupId(), request.buncheolMembers());

    Buncheol buncheol = buncheolDomainService.createBuncheol(hostId, request.toParams());

    // 분철 멤버 저장
    buncheolMemberDomainService.createBuncheolMembers(buncheol.getId(), buncheolMemberParams);

    // 분철 이미지 저장
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
    buncheol.validateRecruiting();

    buncheolImageDomainService.validateImageCount(request.keepImageIds().size() + images.size());

    groupDomainService.validateGroupExists(request.groupId());

    boolean hasParticipants = participationRepository.existsActiveByBuncheolId(buncheolId);

    // 분철에 참여가 하나도 없는 경우
    if (!hasParticipants) {
      modifyWithoutParticipants(buncheol, buncheolId, request);
    } else { // 분철에 참여가 하나 이상 존재하는 경우
      modifyWithParticipants(buncheol, buncheolId, request);
    }

    // 이미지 처리
    buncheolImageDomainService.deleteImagesExcluding(buncheolId, request.keepImageIds());
    if (!images.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolImageUploadEvent(buncheolId, images));
    }
  }

  public void advanceBuncheolStatus(
      final Long hostId, final Long buncheolId, final BuncheolStatus nextStatus) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    final BuncheolStatus previousStatus = buncheol.getStatus();
    buncheolDomainService.advanceBuncheolStatus(buncheol, nextStatus, previousStatus);
  }

  public void cancelBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    // TODO: 추후 참여자 존재 시 패널티 부과 및 환불 처리 분기 추가
    final BuncheolStatus previousStatus = buncheol.getStatus();
    buncheolDomainService.cancelBuncheol(buncheol, previousStatus);
  }

  /** 참여자 없는 경우 분철 수정: 전체 업데이트 + 멤버 삭제 후 재생성 */
  private void modifyWithoutParticipants(
      final Buncheol buncheol, final Long buncheolId, final BuncheolModifyRequest request) {
    // 분철 정보 업데이트
    buncheolDomainService.updateBuncheol(buncheol, request.toParams());

    List<BuncheolMemberParams> memberParams =
        toBuncheolMemberParams(request.groupId(), request.buncheolMembers());
    // 멤버 전체삭제 후 재생성
    buncheolMemberDomainService.deleteAllByBuncheolId(buncheolId);
    buncheolMemberDomainService.createBuncheolMembers(buncheolId, memberParams);
  }

  /** 참여자 있는 경우 분철 수정: 일부 항목 수정 제한 */
  private void modifyWithParticipants(
      final Buncheol buncheol, final Long buncheolId, final BuncheolModifyRequest request) {
    BuncheolParams requestedState = request.toParams();

    // 분철 활성 참여자들이 선택한 배송 방법 조회
    Set<ShippingMethod> usedShippingMethods =
        participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId);

    // 수정 불가 필드 + 배송비 검증
    buncheolModificationPolicy.validateBuncheolFieldChange(
        buncheol, requestedState, usedShippingMethods);

    // 분철 정보 업데이트
    buncheolDomainService.updateBuncheol(buncheol, requestedState);

    // 멤버 조정
    List<MemberParticipationPresence> presences =
        participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId);
    reconcileMembers(buncheolId, request.groupId(), request.buncheolMembers(), presences);
  }

  private void reconcileMembers(
      final Long buncheolId,
      final Long groupId,
      final List<BuncheolMemberRequest> requestMembers,
      final List<MemberParticipationPresence> presences) {
    List<BuncheolMember> existingMembers =
        buncheolMemberDomainService.findAllByBuncheolId(buncheolId);
    // buncheolMemberId -> Entity map으로 변환
    Map<Long, BuncheolMember> existingIdMap = toMap(existingMembers);
    Map<Long, MemberParticipationPresence> presenceMap = toPresenceMap(presences);

    List<BuncheolMemberRequest> newRequests = new ArrayList<>();
    List<BuncheolMemberRequest> existingRequests = new ArrayList<>();
    classifyRequests(requestMembers, newRequests, existingRequests);

    Set<Long> requestedExistingIds =
        existingRequests.stream()
            .map(BuncheolMemberRequest::buncheolMemberId)
            .collect(Collectors.toSet());

    deleteRemovedMembers(existingMembers, requestedExistingIds, presenceMap);
    updateExistingMembers(existingRequests, existingIdMap, presenceMap);
    createNewMembers(buncheolId, groupId, newRequests);
  }

  private void classifyRequests(
      final List<BuncheolMemberRequest> requestMembers,
      final List<BuncheolMemberRequest> newRequests,
      final List<BuncheolMemberRequest> existingRequests) {
    Set<Long> seenIds = new HashSet<>();
    for (BuncheolMemberRequest req : requestMembers) {
      if (req.buncheolMemberId() == null) {
        newRequests.add(req);
      } else {
        if (!seenIds.add(req.buncheolMemberId())) {
          throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DUPLICATED);
        }
        existingRequests.add(req);
      }
    }
  }

  private void deleteRemovedMembers(
      final List<BuncheolMember> existingMembers,
      final Set<Long> requestedExistingIds,
      final Map<Long, MemberParticipationPresence> presenceMap) {
    for (BuncheolMember existing : existingMembers) {
      if (requestedExistingIds.contains(existing.getId())) {
        continue;
      }
      buncheolModificationPolicy.validateMemberDeletion(presenceMap.get(existing.getId()));
      buncheolMemberDomainService.deleteById(existing.getId());
    }
  }

  private void updateExistingMembers(
      final List<BuncheolMemberRequest> existingRequests,
      final Map<Long, BuncheolMember> existingIdMap,
      final Map<Long, MemberParticipationPresence> presenceMap) {
    for (BuncheolMemberRequest req : existingRequests) {
      BuncheolMember existing = existingIdMap.get(req.buncheolMemberId());
      if (existing == null) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_NOT_FOUND);
      }

      MemberParticipationPresence presence = presenceMap.get(req.buncheolMemberId());
      buncheolModificationPolicy.validateMemberPricingChange(existing, presence, req.bidMinPrice());

      // managed 엔티티이므로 도메인 메서드 호출만으로 트랜잭션 커밋 시 dirty UPDATE 가 자동 발행된다.
      existing.updateBidMinPrice(req.bidMinPrice());
    }
  }

  private void createNewMembers(
      final Long buncheolId, final Long groupId, final List<BuncheolMemberRequest> newRequests) {
    if (newRequests.isEmpty()) {
      return;
    }
    List<BuncheolMemberParams> newParams = toBuncheolMemberParams(groupId, newRequests);
    buncheolMemberDomainService.createBuncheolMembers(buncheolId, newParams);
  }

  private Map<Long, BuncheolMember> toMap(final List<BuncheolMember> members) {
    return members.stream().collect(Collectors.toMap(BuncheolMember::getId, Function.identity()));
  }

  private Map<Long, MemberParticipationPresence> toPresenceMap(
      final List<MemberParticipationPresence> presences) {
    return presences.stream()
        .collect(
            Collectors.toMap(MemberParticipationPresence::buncheolMemberId, Function.identity()));
  }

  private List<BuncheolMemberParams> toBuncheolMemberParams(
      final Long groupId, final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = extractAndValidateMemberIds(requests);
    // 멤버 ID가 모두 해당 그룹에 속하는지 검증 (반환값은 사용하지 않음)
    groupDomainService.getGroupMembersByIdsInGroup(groupId, memberIds);

    return requests.stream()
        .map(m -> new BuncheolMemberParams(m.memberId(), m.bidMinPrice()))
        .toList();
  }

  private List<Long> extractAndValidateMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();

    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    return memberIds;
  }
}
