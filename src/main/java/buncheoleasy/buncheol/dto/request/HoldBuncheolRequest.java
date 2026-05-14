package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record HoldBuncheolRequest(
    @NotNull Long groupId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 300) String description,
    @NotBlank @Size(max = 200) String purchaseSite,
    @NotNull @Future Instant deadline,
    @Positive Integer gs25ShippingFee,
    @Positive Integer cuShippingFee,
    @NotEmpty @Valid List<BuncheolMemberRequest> buncheolMembers) {

  public BuncheolParams toParams() {
    return new BuncheolParams(
        groupId, title, description, purchaseSite, deadline, gs25ShippingFee, cuShippingFee);
  }
}
