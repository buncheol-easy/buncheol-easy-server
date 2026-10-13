package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 개최 뒤 빈 자리 하나의 가격을 바꾸는 요청.
 *
 * @param price 멤버 1명당 금액. 0 이상·100원 단위 (개최와 같은 규칙)
 */
public record BuncheolMemberPriceRequest(@NotNull @PositiveOrZero Long price) {}
