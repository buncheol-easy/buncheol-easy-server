package buncheoleasy.buncheol.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import buncheoleasy.buncheol.domain.BuncheolParams;
import java.time.LocalDateTime;
import java.util.List;

public record BuncheolModifyRequest(
        Long groupId,
        @Size(max = 100) String groupName,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 300) String description,
        @NotBlank @Size(max = 200) String goodsName,
        @NotBlank @Size(max = 200) String storeName,
        @Positive long originalPrice,
        @NotNull @Future LocalDateTime deadline,
        @Positive int shippingDeadlineDays,
        @Positive Integer gs25ShippingFee,
        @Positive Integer cuShippingFee,
        @NotBlank @Size(max = 50) String settlementBank,
        @NotBlank @Size(max = 50) String settlementAccount,
        @NotBlank @Size(max = 50) String settlementHolder,
        @NotNull List<Long> keepImageIds,
        @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers
) {
    // 커스텀 그룹 생성 시(groupId == null)에만 groupName 필수
    @AssertTrue
    public boolean isGroupNameValidForGroupType() {
        if (groupId != null) {
            return true;
        }
        return groupName != null && !groupName.isBlank();
    }

    public BuncheolParams toParams(final Long resolvedGroupId, final String resolvedGroupName) {
        return new BuncheolParams(
                resolvedGroupId,
                resolvedGroupName,
                title,
                description,
                goodsName,
                storeName,
                originalPrice,
                deadline,
                shippingDeadlineDays,
                gs25ShippingFee,
                cuShippingFee,
                settlementBank,
                settlementAccount,
                settlementHolder
        );
    }
}
