package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
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
 * @param thumbnailIndex 대표사진으로 쓸 이미지의 images 파트 내 인덱스(0-base, 필수). 이미지 저장 순서는 업로드 순서를 그대로 따르고, 대표사진
 *     여부만 별도 플래그로 기록된다.
 */
public record HoldBuncheolRequest(
    @NotNull Long groupId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 700) String description,
    @NotBlank @Size(max = 200) String purchaseSite,
    @NotNull @Future Instant deadline,
    @NotNull @Positive Integer minHeadcount,
    @PositiveOrZero Integer gs25ShippingFee,
    @PositiveOrZero Integer cuShippingFee,
    @Size(max = 200) String openChatUrl,
    @NotNull @PositiveOrZero Integer thumbnailIndex,
    @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers) {

  // 플로우는 요청이 아니라 서버가 결정한다(운영진=LEGACY 선택 가능, 일반 유저=강제 C2C — docs/46 §3-8).
  public BuncheolParams toParams(final FlowType flowType) {
    return new BuncheolParams(
        groupId,
        title,
        description,
        purchaseSite,
        deadline,
        minHeadcount,
        gs25ShippingFee,
        cuShippingFee,
        flowType,
        openChatUrl);
  }
}
