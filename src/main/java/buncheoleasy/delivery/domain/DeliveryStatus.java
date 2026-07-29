package buncheoleasy.delivery.domain;

import java.util.Set;

public enum DeliveryStatus {
  SNAPSHOTTED,
  SHIPPING,
  DELIVERED,
  RECEIVED;

  // 배송이 끝난 상태(운송사 배송완료 이후). 수령 확인(RECEIVED)은 참여자 버튼에 달려 있어 강제할 수 없으므로,
  // 탈퇴 가드의 "참여/분철 종료" 판정은 DELIVERED 부터 종료로 본다.
  private static final Set<DeliveryStatus> FINISHED = Set.of(DELIVERED, RECEIVED);

  public static Set<DeliveryStatus> finished() {
    return FINISHED;
  }
}
