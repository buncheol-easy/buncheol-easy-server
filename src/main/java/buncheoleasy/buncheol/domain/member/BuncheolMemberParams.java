package buncheoleasy.buncheol.domain.member;

public record BuncheolMemberParams(
    Long memberId, String memberName, long instantPrice, boolean bidAllowed, Long bidMinPrice) {}
