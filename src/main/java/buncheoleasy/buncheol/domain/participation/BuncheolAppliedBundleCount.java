package buncheoleasy.buncheol.domain.participation;

/** 분철별 신청(APPLIED) 묶음 수 집계. 진행 확정 분철에서는 수락 전 추가 신청 묶음 수다(「수락해 주세요」 배지). */
public record BuncheolAppliedBundleCount(Long buncheolId, long count) {}
