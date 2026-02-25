package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BuncheolMemberRequest(
        Long memberId,
        @Size(max = 100) String memberName,
        @Positive long instantPrice,
        boolean bidAllowed,
        @Positive Long bidMinPrice
) {
    // 제시 허용일 경우 제시 최소 금액 필수
    @AssertTrue
    public boolean isBidMinPriceValidWhenBidAllowed() {
        if (!bidAllowed) {
            return true;
        }
        return bidMinPrice != null;
    }

    // 커스텀 멤버 생성 시(memberId == null)에만 memberName 필수
    @AssertTrue
    public boolean isMemberNameValidForMemberType() {
        if (memberId != null) {
            return true;
        }
        return memberName != null && !memberName.isBlank();
    }
}
