package buncheoleasy.buncheol.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 분철 단위 참여 플로우 구분 (docs/46 §0.1 — 그랜드파더링 병존). 기존 분철·운영진 이벤트 분철은 LEGACY 로 현행 그대로 완주하고, 새 플로우는 C2C 분철에만
 * 적용한다. LEGACY 경로 코드는 수정하지 않는 것이 원칙이며, 분기 지점은 docs/46 §3 참조.
 */
@Getter
@RequiredArgsConstructor
public enum FlowType {
  // 즉시 입금(30분) + 페이액션 자동확인. 운영진(can_host) 개최 전용.
  LEGACY("운영진 개최"),
  // 신청(무입금) → 개최자 확정 → 일괄 입금(24h) → "보냈어요" → 개최자 수동 확인. 개최자 개인 계좌 직거래.
  C2C("사용자 개최");

  private final String description;
}
