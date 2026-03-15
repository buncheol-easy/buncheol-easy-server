package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuncheolService {

  private final BuncheolDomainService buncheolDomainService;
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

    // 이미지 처리 (공통)
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
    // 잠긴 필드 검증
    validateLockedFields(buncheol, request, resolvedGroup);

    // 배송비 검증
    Set<ShippingMethod> usedShippingMethods =
        participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId);
    validateShippingFeeModification(buncheol, request, usedShippingMethods);

    // 부분 업데이트
    buncheolDomainService.updateBuncheolPartial(buncheol, request.toPartialParams());

    // 멤버 조정
    List<MemberParticipationPresence> presences =
        participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId);
    reconcileMembers(buncheolId, resolvedGroup.groupId(), request.buncheolMembers(), presences);
  }

  private void validateLockedFields(
      final Buncheol buncheol,
      final BuncheolModifyRequest request,
      final ResolvedGroup resolvedGroup) {
    if (!Objects.equals(buncheol.getGroupId(), resolvedGroup.groupId())
        || !Objects.equals(buncheol.getGroupName(), resolvedGroup.groupName())
        || !Objects.equals(buncheol.getGoodsName(), request.goodsName())
        || !Objects.equals(buncheol.getStoreName(), request.storeName())
        || buncheol.getOriginalPrice() != request.originalPrice()
        || buncheol.getShippingDeadlineDays() != request.shippingDeadlineDays()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_FIELD_LOCKED);
    }
  }

  /** 수정하려는 배송방법을 선택하여 분철에 참여한 참여자가 있다면 예외를 발생시키는 메서드 */
  private void validateShippingFeeModification(
      final Buncheol buncheol,
      final BuncheolModifyRequest request,
      final Set<ShippingMethod> usedShippingMethods) {
    if (usedShippingMethods.contains(ShippingMethod.GS25_HALF)
        && !Objects.equals(
            buncheol.getShippingFeePolicy().gs25ShippingFee(), request.gs25ShippingFee())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }
    if (usedShippingMethods.contains(ShippingMethod.CU_HALF)
        && !Objects.equals(
            buncheol.getShippingFeePolicy().cuShippingFee(), request.cuShippingFee())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_SHIPPING_FEE_LOCKED);
    }
  }

  private void reconcileMembers(
      final Long buncheolId,
      final Long resolvedGroupId,
      final List<BuncheolMemberRequest> requestMembers,
      final List<MemberParticipationPresence> presences) {
    List<BuncheolMember> existingMembers =
        buncheolMemberDomainService.findAllByBuncheolId(buncheolId);
    Map<Long, BuncheolMember> existingMap =
        existingMembers.stream()
            .collect(Collectors.toMap(BuncheolMember::getId, Function.identity()));
    Map<Long, MemberParticipationPresence> presenceMap =
        presences.stream()
            .collect(
                Collectors.toMap(
                    MemberParticipationPresence::buncheolMemberId, Function.identity()));

    // 요청 멤버를 신규/기존으로 분류
    Map<Boolean, List<BuncheolMemberRequest>> partitioned =
        requestMembers.stream()
            .collect(Collectors.partitioningBy(req -> req.buncheolMemberId() == null));
    List<BuncheolMemberRequest> newRequests = partitioned.get(true);
    List<BuncheolMemberRequest> existingRequests = partitioned.get(false);

    List<Long> existingIds =
        existingRequests.stream().map(BuncheolMemberRequest::buncheolMemberId).toList();
    if (existingIds.size() != new HashSet<>(existingIds).size()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DUPLICATED);
    }
    Set<Long> requestedExistingIds = new HashSet<>(existingIds);

    deleteRemovedMembers(existingMembers, requestedExistingIds, presenceMap);
    updateExistingMembers(existingRequests, existingMap, presenceMap);
    createNewMembers(buncheolId, resolvedGroupId, newRequests);
  }

  private void deleteRemovedMembers(
      final List<BuncheolMember> existingMembers,
      final Set<Long> requestedExistingIds,
      final Map<Long, MemberParticipationPresence> presenceMap) {
    for (BuncheolMember existing : existingMembers) {
      // 삭제하지 않는다면
      if (requestedExistingIds.contains(existing.getId())) {
        continue;
      }
      // 삭제할건데, 참여자가 있는 분철 멤버라면?
      if (presenceMap.containsKey(existing.getId())) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_DELETE_LOCKED);
      }
      buncheolMemberDomainService.deleteById(existing.getId());
    }
  }

  private void updateExistingMembers(
      final List<BuncheolMemberRequest> existingRequests,
      final Map<Long, BuncheolMember> existingMap,
      final Map<Long, MemberParticipationPresence> presenceMap) {
    for (BuncheolMemberRequest req : existingRequests) {
      BuncheolMember existing = existingMap.get(req.buncheolMemberId());
      if (existing == null) {
        throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_NOT_FOUND);
      }

      MemberParticipationPresence presence = presenceMap.get(req.buncheolMemberId());
      if (presence == null) {
        existing.updatePricing(req.instantPrice(), req.bidAllowed(), req.bidMinPrice());
        buncheolMemberDomainService.updateBuncheolMember(existing);
      } else if (presence.hasActiveInstant()) { // 즉시구매 참여가 존재할 경우
        validateInstantMemberUnchanged(existing, req);
      } else if (presence.hasActiveBid()) { // 제시 참여가 존재할 경우
        validateBidOnlyMemberModification(existing, req);
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

  private void validateInstantMemberUnchanged(
      final BuncheolMember existing, final BuncheolMemberRequest req) {
    if (existing.getInstantPrice() != req.instantPrice()
        || existing.getBidOption().bidAllowed() != req.bidAllowed()
        || !Objects.equals(existing.getBidOption().bidMinPrice(), req.bidMinPrice())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_MEMBER_PRICE_LOCKED);
    }
  }

  private void validateBidOnlyMemberModification(
      final BuncheolMember existing, final BuncheolMemberRequest req) {
    // bidAllowed 비활성화 불가 (제시 참여자가 있으므로)
    if (existing.getBidOption().bidAllowed() && !req.bidAllowed()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_BID_DISABLE_LOCKED);
    }
    // bidMinPrice 올리기 불가
    if (existing.getBidOption().bidAllowed()
        && req.bidMinPrice() != null
        && existing.getBidOption().bidMinPrice() != null
        && req.bidMinPrice() > existing.getBidOption().bidMinPrice()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MODIFY_BID_MIN_INCREASE_LOCKED);
    }
  }

  // ── 공통 유틸 ──

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
