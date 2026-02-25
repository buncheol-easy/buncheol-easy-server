package buncheoleasy.buncheol.domain.member;

public record BuncheolMemberParams(
        Long memberId,
        String memberName,
        Long instantPrice,
        boolean bidAllowed,
        Long bidMinPrice
) {
}
