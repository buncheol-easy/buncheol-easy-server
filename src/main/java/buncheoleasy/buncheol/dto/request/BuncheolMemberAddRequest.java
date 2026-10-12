package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 개최 뒤 그룹 멤버를 자리로 다시 넣는 요청. 접근 정책은 받지 않는다 — C2C 전용 API 이고 C2C 에는 선착순 자리만 둘 수 있다.
 *
 * @param memberId 그룹 멤버 id ({@code group_members.id}). 분철의 그룹에 속해야 한다
 * @param price 멤버 1명당 금액. 0 이상·100원 단위 (개최와 같은 규칙)
 */
public record BuncheolMemberAddRequest(
    @NotNull Long memberId, @NotNull @PositiveOrZero Long price) {}
