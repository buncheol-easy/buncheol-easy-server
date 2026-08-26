package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.domain.member.SlotAccessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 0원 슬롯은 오픈 이벤트 무료 분철(운영진 발행) 용도로 허용한다.
 *
 * @param accessType 슬롯 접근 정책. 생략(null)하면 선착순(OPEN) — 필드를 안 보내는 구 클라이언트 호환
 */
public record BuncheolMemberRequest(
    @NotNull Long memberId, @NotNull @PositiveOrZero Long price, SlotAccessType accessType) {

  public BuncheolMemberRequest(final Long memberId, final Long price) {
    this(memberId, price, null);
  }

  public BuncheolMemberParams toParams() {
    return new BuncheolMemberParams(memberId, price, accessType);
  }
}
