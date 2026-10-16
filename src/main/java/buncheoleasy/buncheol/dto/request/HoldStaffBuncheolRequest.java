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
 * 운영진 분철 개최 요청. 회원 개최 요청({@link HoldBuncheolRequest})과 칸이 같고, 개최 방식은 고를 수 없으며(언제나 운영진 분철) 예약 오픈
 * 시각을 더한다.
 *
 * @param opensAt 예약 오픈 시각(선택). 없으면 등록하자마자 열린다. 있으면 지금 이후·마감 전이어야 하고(아니면 BCH-129), 초 아래는
 *     버린다. {@code @Future} 를 달지 않는다 — 달면 C-001 이 먼저 나가 같은 잘못에 오류가 둘로 갈린다.
 */
public record HoldStaffBuncheolRequest(
    @NotNull Long groupId,
    @NotBlank @Size(max = 64) String title,
    @Size(max = 700) String description,
    @NotBlank @Size(max = 200) String purchaseSite,
    @NotNull @Future Instant deadline,
    Instant opensAt,
    @NotNull @Positive Integer minHeadcount,
    @PositiveOrZero Integer gs25ShippingFee,
    @PositiveOrZero Integer cuShippingFee,
    @Size(max = 200) String openChatUrl,
    @NotNull @PositiveOrZero Integer thumbnailIndex,
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
        cuShippingFee,
        FlowType.LEGACY,
        openChatUrl);
  }
}
