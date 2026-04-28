package buncheoleasy.buncheol.domain.member;

public record BuncheolMemberParams(
    Long memberId,
    String memberName,
    String memberImage,
    long instantPrice,
    boolean bidAllowed,
    Long bidMinPrice) {}
