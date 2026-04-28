package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record BuncheolModifyRequest(
    @NotNull Long groupId,
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
    @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers) {

  public BuncheolParams toParams(
      final Long resolvedGroupId, final String resolvedGroupName, final String resolvedGroupImage) {
    return new BuncheolParams(
        resolvedGroupId,
        resolvedGroupName,
        resolvedGroupImage,
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
        settlementHolder);
  }
}
