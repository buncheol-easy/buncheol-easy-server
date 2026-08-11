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
 * @param flowType 개최 방식(선택). null 이면 서버가 결정한다 — 일반 유저 = C2C 강제(LEGACY 요청은 USR-031 거부), 운영진(can_host) =
 *     LEGACY 기본에 C2C 선택 가능 (docs/46 §3-8).
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
    FlowType flowType,
    @NotNull @PositiveOrZero Integer thumbnailIndex,
    @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers) {

  // 인자의 resolvedFlowType 은 서버 결정값 — 요청 필드 flowType 을 그대로 쓰지 않는다(자격 게이트 경유).
  public BuncheolParams toParams(final FlowType resolvedFlowType) {
    return new BuncheolParams(
        groupId,
        title,
        description,
        purchaseSite,
        deadline,
        minHeadcount,
        gs25ShippingFee,
        cuShippingFee,
        resolvedFlowType,
        openChatUrl);
  }
}
