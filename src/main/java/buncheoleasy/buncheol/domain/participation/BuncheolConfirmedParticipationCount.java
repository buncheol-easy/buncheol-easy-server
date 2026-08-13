package buncheoleasy.buncheol.domain.participation;

/** 분철별 입금확인(CONFIRMED) 참여 수 집계 — 개최 목록의 취소 가능 여부 판정용 (docs/56 S-2). */
public record BuncheolConfirmedParticipationCount(Long buncheolId, long count) {}
