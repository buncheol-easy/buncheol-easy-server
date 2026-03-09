package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;

public record ParticipationResponse(Long participationId, ParticipationStatus status) {

  public static ParticipationResponse from(final Participation participation) {
    return new ParticipationResponse(participation.getId(), participation.getStatus());
  }
}
