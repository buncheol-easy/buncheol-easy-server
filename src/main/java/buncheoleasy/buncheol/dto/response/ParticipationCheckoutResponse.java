package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;

public record ParticipationCheckoutResponse(
    Long participationId, ParticipationStatus participationStatus, long bidAmount) {

  public static ParticipationCheckoutResponse from(final Participation participation) {
    return new ParticipationCheckoutResponse(
        participation.getId(), participation.getStatus(), participation.getBidAmount());
  }
}
