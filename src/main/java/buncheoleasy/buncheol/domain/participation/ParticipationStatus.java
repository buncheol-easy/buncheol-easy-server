package buncheoleasy.buncheol.domain.participation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipationStatus {
  ACTIVE_BID("제시 진행 중"),
  AWAITING_PAYMENT("낙찰자 결제 대기"),
  CONFIRMED("참여 확정"),
  CANCELLED("참여 취소"),
  FAILED("참여 실패");

  private final String description;
}
