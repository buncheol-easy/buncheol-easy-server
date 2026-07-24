package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// 0원 슬롯은 오픈 이벤트 무료 분철(운영진 발행) 용도로 허용한다.
public record BuncheolMemberRequest(@NotNull Long memberId, @NotNull @PositiveOrZero Long price) {

  public BuncheolMemberParams toParams() {
    return new BuncheolMemberParams(memberId, price);
  }
}
