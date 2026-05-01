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
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.ArrayList;
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
  private final UserDomainService userDomainService;
  private final ApplicationEventPublisher eventPublisher;

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
    buncheol.validateRecruiting();

    buncheolImageDomainService.validateImageCount(request.keepImageIds().size() + images.size());

    // 멤버 ID 중복은 fail-fast 로 미리 검증. groupId 는 분철 생성 시 고정이라 요청에 포함하지 않는다.
    Set<Long> requestedMemberIds = Set.copyOf(extractDistinctMemberIds(request.buncheolMembers()));

    Long groupId = buncheol.getGroupId();
    BuncheolParams requestedState = request.toParams(groupId);
    boolean hasParticipants = participationRepository.existsActiveByBuncheolId(buncheolId);

    // 활성 참여가 있으면 잠긴 필드/배송비 변경 정책 검증
    if (hasParticipants) {
      Set<ShippingMethod> usedShippingMethods =
          participationRepository.findActiveShippingMethodsByBuncheolId(buncheolId);
      buncheolModificationPolicy.validateBuncheolFieldChange(
          buncheol, requestedState, usedShippingMethods);
    }

    buncheolDomainService.updateBuncheol(buncheol, requestedState);

    List<MemberParticipationPresence> presences =
        hasParticipants
            ? participationRepository.findActiveParticipationPresencesByBuncheolId(buncheolId)
            : List.of();
    reconcileMembers(buncheolId, groupId, request.buncheolMembers(), requestedMemberIds, presences);

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

  /**
   * 요청 멤버 목록과 기존 멤버를 memberId 기준으로 reconcile. 멤버 ID 중복은 호출자가 미리 끝낸다.
   *
   * <ul>
   *   <li>기존O 요청O → bidMinPrice 갱신 (활성 참여 있으면 정책 검증)
   *   <li>기존X 요청O → 신규 추가 (그룹 소속 검증)
   *   <li>기존O 요청X → 삭제 (활성 참여 있으면 거부)
   * </ul>
   */
  private void reconcileMembers(
      final Long buncheolId,
      final Long groupId,
      final List<BuncheolMemberRequest> requestMembers,
      final Set<Long> requestedMemberIds,
      final List<MemberParticipationPresence> presences) {
    List<BuncheolMember> existingMembers =
        buncheolMemberDomainService.findAllByBuncheolId(buncheolId);
    PresenceLookup presenceLookup = PresenceLookup.from(presences);

    deleteUnrequestedMembers(existingMembers, requestedMemberIds, presenceLookup);
    upsertRequestedMembers(buncheolId, groupId, requestMembers, existingMembers, presenceLookup);
  }

  private void deleteUnrequestedMembers(
      final List<BuncheolMember> existingMembers,
      final Set<Long> requestedMemberIds,
      final PresenceLookup presenceLookup) {
    for (BuncheolMember existing : existingMembers) {
      if (requestedMemberIds.contains(existing.getMemberId())) {
        continue;
      }
      buncheolModificationPolicy.validateMemberDeletion(presenceLookup.of(existing));
      buncheolMemberDomainService.deleteById(existing.getId());
    }
  }

  private void upsertRequestedMembers(
      final Long buncheolId,
      final Long groupId,
      final List<BuncheolMemberRequest> requestMembers,
      final List<BuncheolMember> existingMembers,
      final PresenceLookup presenceLookup) {
    // (buncheol_id, member_id) UNIQUE 가 보장하지만 옛 데이터 오염 시 IllegalStateException 대신 첫 항목 유지.
    Map<Long, BuncheolMember> existingByMemberId =
        existingMembers.stream()
            .collect(
                Collectors.toMap(BuncheolMember::getMemberId, Function.identity(), (a, b) -> a));

    List<BuncheolMemberParams> toCreate = new ArrayList<>();
    for (BuncheolMemberRequest req : requestMembers) {
      BuncheolMember existing = existingByMemberId.get(req.memberId());
      if (existing == null) {
        toCreate.add(req.toParams());
        continue;
      }
      buncheolModificationPolicy.validateMemberPricingChange(
          existing, presenceLookup.of(existing), req.bidMinPrice());
      // managed 엔티티이므로 도메인 메서드 호출만으로 트랜잭션 커밋 시 dirty UPDATE 가 자동 발행된다.
      existing.updateBidMinPrice(req.bidMinPrice());
    }

    if (toCreate.isEmpty()) {
      return;
    }
    // 신규 멤버는 분철의 그룹에 속해야 한다. 기존 멤버는 groupId 가 불변이므로 재검증 불필요.
    List<Long> newMemberIds = toCreate.stream().map(BuncheolMemberParams::memberId).toList();
    groupDomainService.getGroupMembersByIdsInGroup(groupId, newMemberIds);
    buncheolMemberDomainService.createBuncheolMembers(buncheolId, toCreate);
  }

  private List<Long> extractDistinctMemberIds(final List<BuncheolMemberRequest> requests) {
    List<Long> memberIds = requests.stream().map(BuncheolMemberRequest::memberId).toList();
    if (memberIds.size() != memberIds.stream().distinct().count()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
    }
    return memberIds;
  }

  /** buncheol_member_id 키의 presence를 BuncheolMember 엔티티로 조회하기 위한 어댑터. */
  private record PresenceLookup(Map<Long, MemberParticipationPresence> byBuncheolMemberId) {

    static PresenceLookup from(final List<MemberParticipationPresence> presences) {
      return new PresenceLookup(
          presences.stream()
              .collect(
                  Collectors.toMap(
                      MemberParticipationPresence::buncheolMemberId, Function.identity())));
    }

    MemberParticipationPresence of(final BuncheolMember member) {
      return byBuncheolMemberId.get(member.getId());
    }
  }
}
