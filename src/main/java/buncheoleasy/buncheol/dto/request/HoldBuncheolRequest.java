package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * @param thumbnailIndex 대표사진으로 쓸 이미지의 images 파트 내 인덱스(0-base). 생략 시 첫 번째 이미지. 이미지 저장 순서는 업로드 순서를 그대로
 *     따르고, 대표사진 여부만 별도 플래그로 기록된다.
 */
public record HoldBuncheolRequest(
    @NotNull Long groupId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 700) String description,
    @NotBlank @Size(max = 200) String purchaseSite,
    @NotNull @Future Instant deadline,
    @NotNull @Positive Integer minHeadcount,
    @Positive Integer gs25ShippingFee,
    @Positive Integer cuShippingFee,
    @PositiveOrZero Integer thumbnailIndex,
    @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers) {

  public BuncheolParams toParams() {
    return new BuncheolParams(
        groupId,
        title,
        description,
        purchaseSite,
        deadline,
        minHeadcount,
        gs25ShippingFee,
        cuShippingFee);
  }
}
