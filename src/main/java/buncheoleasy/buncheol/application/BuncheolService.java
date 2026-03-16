package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolModificationPolicy;
import buncheoleasy.buncheol.domain.BuncheolParams;
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
import buncheoleasy.group.domain.member.GroupMember;
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

    ResolvedGroup resolvedGroup = resolveGroup(request.groupId(), request.groupName());

    List<BuncheolMemberParams> buncheolMemberParams =
        toBuncheolMemberParams(resolvedGroup.groupId(), request.buncheolMembers());

    // groupId가 있으면 groupName은 DB값으로 채워짐 (요청값 무시), 없으면 요청값 사용
    Buncheol buncheol =
        buncheolDomainService.createBuncheol(
            hostId, request.toParams(resolvedGroup.groupId(), resolvedGroup.groupName()));

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

    ResolvedGroup resolvedGroup = resolveGroup(request.groupId(), request.groupName());

    boolean hasParticipants = participationRepository.existsActiveByBuncheolId(buncheolId);

    // 분철에 참여가 하나도 없는 경우
    if (!hasParticipants) {
      modifyWithoutParticipants(buncheol, buncheolId, request, resolvedGroup);
    } else { // 분철에 참여가 하나 이상 존재하는 경우
      modifyWithParticipants(buncheol, buncheolId, request, resolvedGroup);
    }

    // 이미지 처리
    buncheolImageDomainService.deleteImagesExcluding(buncheolId, request.keepImageIds());
    if (!images.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolImageUploadEvent(buncheolId, images));
    }
  }

  public void cancelBuncheol(final Long hostId, final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateOwner(hostId);
    // TODO: 추후 참여자 존재 시 패널티 부과 및 환불 처리 분기 추가
    buncheolDomainService.cancelBuncheol(buncheol);
  }

  /** 참여자 없는 경우 분철 수정: 전체 업데이트 + 멤버 삭제 후 재생성 */
  private void modifyWithoutParticipants(
      final Buncheol buncheol,
      final Long buncheolId,
      final BuncheolModifyRequest request,
      final ResolvedGroup resolvedGroup) {
    // 분철 정보 업데이트
    buncheolDomainService.updateBuncheol(
        buncheol, request.toParams(resolvedGroup.groupId(), resolvedGroup.groupName()));

    List<BuncheolMemberParams> memberParams =
        toBuncheolMemberParams(resolvedGroup.groupId(), request.buncheolMembers());
    // 멤버 전체삭제 후 재생성
    buncheolMemberDomainService.deleteAllByBuncheolId(buncheolId);
    buncheolMemberDomainService.createBuncheolMembers(buncheolId, memberParams);
  }

  /** 참여자 있는 경우 분철 수정: 일부 항목 수정 제한 */
  private void modifyWithParticipants(
      final Buncheol buncheol,
      final Long buncheolId,
      final BuncheolModifyRequest request,
      final ResolvedGroup resolvedGroup) {
    BuncheolParams requestedState =
        request.toParams(resolvedGroup.groupId(), resolvedGroup.groupName());

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
    reconcileMembers(buncheolId, resolvedGroup.groupId(), request.buncheolMembers(), presences);
  }

  private void reconcileMembers(
      final Long buncheolId,
      final Long resolvedGroupId,
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
    createNewMembers(buncheolId, resolvedGroupId, newRequests);
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
      buncheolModificationPolicy.validateMemberPricingChange(
          existing, presence, req.instantPrice(), req.bidAllowed(), req.bidMinPrice());

      if (presence == null || !presence.hasActiveInstant()) {
        existing.updatePricing(req.instantPrice(), req.bidAllowed(), req.bidMinPrice());
        buncheolMemberDomainService.updateBuncheolMember(existing);
      }
    }
  }

  private void createNewMembers(
      final Long buncheolId,
      final Long resolvedGroupId,
      final List<BuncheolMemberRequest> newRequests) {
    if (newRequests.isEmpty()) {
      return;
    }
    List<BuncheolMemberParams> newParams = toBuncheolMemberParams(resolvedGroupId, newRequests);
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

  // groupId가 있으면 존재 검증 후 반환, 없으면 null 반환 (그룹명은 분철에 직접 저장)
  private ResolvedGroup resolveGroup(final Long groupId, final String requestedGroupName) {
    if (groupId == null) {
      return new ResolvedGroup(null, requestedGroupName);
    }
    return new ResolvedGroup(groupId, groupDomainService.getGroup(groupId).getName());
  }

  private List<BuncheolMemberParams> toBuncheolMemberParams(
      final Long resolvedGroupId, final List<BuncheolMemberRequest> requests) {
    if (resolvedGroupId == null) {
      return buildCustomGroupMembers(requests);
    }
    return buildOfficialGroupMembers(resolvedGroupId, requests);
  }

  private List<BuncheolMemberParams> buildCustomGroupMembers(
      final List<BuncheolMemberRequest> requests) {
    if (requests.stream().anyMatch(m -> m.memberName() == null || m.memberName().isBlank())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
    }

    return requests.stream()
        .map(
            m ->
                new BuncheolMemberParams(
                    null, m.memberName(), m.instantPrice(), m.bidAllowed(), m.bidMinPrice()))
        .toList();
  }

  private List<BuncheolMemberParams> buildOfficialGroupMembers(
      final Long groupId, final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = extractAndValidateOfficialMemberIds(requests);
    Map<Long, String> memberNames = loadMemberNameMapInGroup(groupId, memberIds);

    return requests.stream()
        .map(
            m ->
                new BuncheolMemberParams(
                    m.memberId(),
                    memberNames.get(m.memberId()),
                    m.instantPrice(),
                    m.bidAllowed(),
                    m.bidMinPrice()))
        .toList();
  }

  private List<Long> extractAndValidateOfficialMemberIds(
      final List<BuncheolMemberRequest> requests) {
    if (requests.stream().anyMatch(m -> m.memberId() == null)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
    }

    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();

    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }

    return memberIds;
  }

  private Map<Long, String> loadMemberNameMapInGroup(
      final Long groupId, final List<Long> memberIds) {
    return groupDomainService.getGroupMembersByIdsInGroup(groupId, memberIds).stream()
        .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));
  }

  private record ResolvedGroup(Long groupId, String groupName) {}
}
