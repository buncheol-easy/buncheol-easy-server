package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import java.util.List;
import java.util.Map;
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
    private final GroupDomainService groupDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void holdBuncheol(final Long hostId, final HoldBuncheolRequest request, final List<ImageFile> images) {
        buncheolImageDomainService.validateImageCount(images.size());

        ResolvedGroup resolvedGroup = resolveGroup(request.groupId(), request.groupName());

        List<BuncheolMemberParams> buncheolMemberParams = toBuncheolMemberParams(
                resolvedGroup.groupId(),
                request.buncheolMembers()
        );

        // groupId가 있으면 groupName은 DB값으로 채워짐 (요청값 무시), 없으면 요청값 사용
        Buncheol buncheol = buncheolDomainService.createBuncheol(
                hostId,
                toBuncheolParams(request, resolvedGroup)
        );

        // 분철 멤버 저장
        buncheolMemberDomainService.createBuncheolMembers(
                buncheol.getId(),
                buncheolMemberParams
        );

        // 분철 이미지 저장
        if (!images.isEmpty()) {
            eventPublisher.publishEvent(
                    new BuncheolImageUploadEvent(
                        buncheol.getId(),
                        images
                    )
            );
        }
    }

    // TODO: 추후 참여자 존재 시 제한된 수정만 허용하는 분기 추가
    @Transactional
    public void modifyBuncheol(final Long hostId, final Long buncheolId,
                               final BuncheolModifyRequest request, final List<ImageFile> images) {
        Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
        buncheol.validateOwner(hostId);

        buncheolImageDomainService.validateImageCount(request.keepImageIds().size() + images.size());

        // 그룹 해석 (공식 그룹이면 DB에서 그룹명 조회)
        ResolvedGroup resolvedGroup = resolveGroup(request.groupId(), request.groupName());

        // 분철 정보 전체 업데이트
        buncheolDomainService.updateBuncheol(buncheol, toBuncheolParams(request, resolvedGroup));

        // 멤버 교체
        List<BuncheolMemberParams> memberParams = toBuncheolMemberParams(
                resolvedGroup.groupId(),
                request.buncheolMembers()
        );
        buncheolMemberDomainService.deleteAllByBuncheolId(buncheolId);
        buncheolMemberDomainService.createBuncheolMembers(buncheolId, memberParams);

        // 기존 이미지 중 유지 대상 외 삭제
        buncheolImageDomainService.deleteImagesExcluding(buncheolId, request.keepImageIds());

        // 새 이미지 업로드
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

    // groupId가 있으면 존재 검증 후 반환, 없으면 null 반환 (그룹명은 분철에 직접 저장)
    private ResolvedGroup resolveGroup(final Long groupId, final String requestedGroupName) {
        if (groupId == null) {
            return new ResolvedGroup(null, requestedGroupName);
        }
        return new ResolvedGroup(
                groupId,
                groupDomainService
                        .getGroup(groupId)
                        .getName()
        );
    }

    private BuncheolParams toBuncheolParams(final HoldBuncheolRequest request, final ResolvedGroup resolvedGroup) {
        return new BuncheolParams(
                resolvedGroup.groupId(),
                resolvedGroup.groupName(),
                request.title(),
                request.description(),
                request.goodsName(),
                request.storeName(),
                request.originalPrice(),
                request.deadline(),
                request.shippingDeadlineDays(),
                request.gs25ShippingFee(),
                request.cuShippingFee(),
                request.settlementBank(),
                request.settlementAccount(),
                request.settlementHolder()
        );
    }

    private BuncheolParams toBuncheolParams(final BuncheolModifyRequest request, final ResolvedGroup resolvedGroup) {
        return new BuncheolParams(
                resolvedGroup.groupId(),
                resolvedGroup.groupName(),
                request.title(),
                request.description(),
                request.goodsName(),
                request.storeName(),
                request.originalPrice(),
                request.deadline(),
                request.shippingDeadlineDays(),
                request.gs25ShippingFee(),
                request.cuShippingFee(),
                request.settlementBank(),
                request.settlementAccount(),
                request.settlementHolder()
        );
    }

    private List<BuncheolMemberParams> toBuncheolMemberParams(final Long resolvedGroupId,
                                                              final List<BuncheolMemberRequest> requests) {
        if (resolvedGroupId == null) {
            return buildCustomGroupMembers(requests);
        }
        return buildOfficialGroupMembers(resolvedGroupId, requests);
    }

    private List<BuncheolMemberParams> buildCustomGroupMembers(final List<BuncheolMemberRequest> requests) {
        if (requests.stream().anyMatch(m -> m.memberName() == null || m.memberName().isBlank())) {
            throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
        }

        return requests.stream()
                .map(m -> new BuncheolMemberParams(
                        null,
                        m.memberName(),
                        m.instantPrice(),
                        m.bidAllowed(),
                        m.bidMinPrice()
                ))
                .toList();
    }

    private List<BuncheolMemberParams> buildOfficialGroupMembers(final Long groupId,
                                                                 final List<BuncheolMemberRequest> requests) {
        List<Long> memberIds = extractAndValidateOfficialMemberIds(requests);
        Map<Long, String> memberNames = loadMemberNameMapInGroup(groupId, memberIds);

        return requests.stream()
                .map(m -> new BuncheolMemberParams(
                        m.memberId(),
                        memberNames.get(m.memberId()),
                        m.instantPrice(),
                        m.bidAllowed(),
                        m.bidMinPrice()
                ))
                .toList();
    }

    private List<Long> extractAndValidateOfficialMemberIds(final List<BuncheolMemberRequest> requests) {
        if (requests.stream().anyMatch(m -> m.memberId() == null)) {
            throw new BusinessException(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
        }

        List<Long> memberIds = requests.stream()
                .map(BuncheolMemberRequest::memberId)
                .toList();

        if (memberIds.size() != memberIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
        }

        return memberIds;
    }

    private Map<Long, String> loadMemberNameMapInGroup(final Long groupId, final List<Long> memberIds) {
        return groupDomainService.getGroupMembersByIdsInGroup(groupId, memberIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                GroupMember::getId,
                                GroupMember::getName
                        )
                );
    }

    private record ResolvedGroup(Long groupId, String groupName) {
    }
}
