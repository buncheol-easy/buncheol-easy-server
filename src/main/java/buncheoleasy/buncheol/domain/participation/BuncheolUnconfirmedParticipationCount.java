package buncheoleasy.buncheol.domain.participation;

/**
 * 분철별 미확인(입금 대기·「보냈어요」) 참여 수 집계 — 「개최한 분철」 목록의 진행 확정 판정용. 진행 확정 가드·관리 화면과 같은
 * 집합({@link ParticipationStatus#payableStatuses()})을 센다.
 */
public record BuncheolUnconfirmedParticipationCount(Long buncheolId, long count) {}
