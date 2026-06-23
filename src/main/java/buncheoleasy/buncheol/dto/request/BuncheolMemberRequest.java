package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BuncheolMemberRequest(@NotNull Long memberId, @NotNull @Positive Long price) {

  public BuncheolMemberParams toParams() {
    return new BuncheolMemberParams(memberId, price);
  }
}
