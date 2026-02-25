package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
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
        if (images != null) {
            buncheolImageDomainService.validateImageCount(images.size());
        }

        // null 또는 groupId
        Long resolvedGroupId = resolveGroup(request.groupId());

        // groupId가 있으면 groupName은 DB값으로 채워짐 (요청값 무시), 없으면 요청값 사용
        Buncheol buncheol = buncheolDomainService.createBuncheol(
                hostId,
                toBuncheolParams(request, resolvedGroupId)
        );

        // 분철 멤버 저장
        buncheolMemberDomainService.createBuncheolMembers(
                buncheol.getId(),
                toBuncheolMemberParams(
                        resolvedGroupId,
                        request.buncheolMembers()
                )
        );

        // 분철 이미지 저장
        if (images != null && !images.isEmpty()) {
            eventPublisher.publishEvent(
                    new BuncheolImageUploadEvent(
                        buncheol.getId(),
                        images
                    )
            );
        }
    }

    // groupId가 있으면 존재 검증 후 반환, 없으면 null 반환 (그룹명은 buncheol에 직접 저장)
    private Long resolveGroup(final Long groupId) {
        if (groupId == null) {
            return null;
        }
        groupDomainService.validateGroupExists(groupId);
        return groupId;
    }

    private BuncheolParams toBuncheolParams(final HoldBuncheolRequest request, final Long resolvedGroupId) {
        String groupName = resolvedGroupId == null
                ? request.groupName()
                : groupDomainService.getGroup(resolvedGroupId).getName();
        return new BuncheolParams(
                resolvedGroupId,
                groupName,
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
            if (requests.stream()
                    .anyMatch(m -> m.memberName() == null
                                    || m.memberName().isBlank())
            ) {
                throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
            }

            return requests.stream()
                    .map(m -> new BuncheolMemberParams(
                            null,
                            m.memberName(),
                            m.instantPrice(),
                            m.bidAllowed(),
                            m.bidMinPrice()
                    )).toList();
        }

        // memberId가 없는 멤버가 있으면 에러
        if (requests.stream()
                .anyMatch(m -> m.memberId() == null)
        ) {
            throw new BusinessException(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
        }

        List<Long> memberIds = requests.stream()
                .map(BuncheolMemberRequest::memberId)
                .toList();

        // 중복 memberId 검증
        long distinctCount = memberIds.stream().distinct().count();
        if (memberIds.size() != distinctCount) {
            throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
        }

        // 해당 그룹 소속 여부 검증
        groupDomainService.validateMembersBelongToGroup(resolvedGroupId, memberIds);

        Map<Long, String> memberNames = groupDomainService.getGroupMembersByIds(memberIds)
                .stream().collect(
                        Collectors.toMap(
                                GroupMember::getId,
                                GroupMember::getName
                        )
                );

        return requests.stream()
                .map(m -> new BuncheolMemberParams(
                        m.memberId(),
                        memberNames.get(m.memberId()),
                        m.instantPrice(),
                        m.bidAllowed(),
                        m.bidMinPrice()
                )).toList();
    }
}
