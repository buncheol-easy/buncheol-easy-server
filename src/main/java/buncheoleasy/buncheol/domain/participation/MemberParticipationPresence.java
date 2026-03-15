package buncheoleasy.buncheol.domain.participation;

public record MemberParticipationPresence(
    Long buncheolMemberId, boolean hasActiveInstant, boolean hasActiveBid) {}
