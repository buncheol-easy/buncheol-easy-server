package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 0원 슬롯은 오픈 이벤트 무료 분철(운영진 발행) 용도로 허용한다.
 *
 * @param accessType 슬롯 접근 정책. 생략(null)하면 선착순(OPEN)
 */
public record BuncheolMemberRequest(
    @NotNull Long memberId, @NotNull @PositiveOrZero Long price, BuncheolMemberAccessType accessType) {

  public BuncheolMemberRequest(final Long memberId, final Long price) {
    this(memberId, price, null);
  }

  public BuncheolMemberParams toParams() {
    return new BuncheolMemberParams(memberId, price, accessType);
  }
}
