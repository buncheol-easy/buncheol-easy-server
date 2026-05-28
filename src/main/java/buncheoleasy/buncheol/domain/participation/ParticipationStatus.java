package buncheoleasy.buncheol.domain.participation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
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

  // 분철이 RECRUITING 일 때 존재할 수 있는 활성 참여 상태들. 호스트 분철 cancel cascade 대상.
  // 향후 RECRUITING 단계에 새 활성 상태가 추가되면 이 집합에 함께 등록한다 (최소 한 개는 유지).
  // 외부 호출자가 mutate 하지 못하도록 unmodifiable view 로 노출한다.
  private static final Set<ParticipationStatus> ACTIVE_UNDER_RECRUITING =
      Collections.unmodifiableSet(EnumSet.of(ACTIVE_BID));

  public static Set<ParticipationStatus> activeUnderRecruiting() {
    return ACTIVE_UNDER_RECRUITING;
  }
}
