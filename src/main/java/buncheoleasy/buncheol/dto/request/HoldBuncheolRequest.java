package buncheoleasy.buncheol.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record HoldBuncheolRequest(
        Long groupId,
        @Size(max = 100) String groupName,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotBlank @Size(max = 200) String goodsName,
        @NotBlank @Size(max = 200) String storeName,
        @Positive int originalPrice,
        @NotNull @Future LocalDateTime deadline,
        @Positive int shippingDeadlineDays,
        @Positive Integer gs25ShippingFee,
        @Positive Integer cuShippingFee,
        @NotBlank @Size(max = 50) String settlementBank,
        @NotBlank @Size(max = 50) String settlementAccount,
        @NotBlank @Size(max = 50) String settlementHolder,
        @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers
) {
    // 커스텀 그룹 생성 시(groupId == null)에만 groupName 필수
    @AssertTrue
    public boolean isGroupNameRequiredForCustomGroup() {
        if (groupId != null) {
            return true;
        }
        return groupName != null && !groupName.isBlank();
    }
}
