package buncheoleasy.buncheol.application;

/**
 * C2C 모집 정원 충족 — 신청으로 전 멤버 슬롯이 활성 참여로 채워짐(미충족→충족 전이마다 발행, 중복 발송 차단은 리스너 가드). 커밋 후
 * 개최자에게 진행 확정 독촉 알림톡 발송을 트리거한다. {@code applicantCount} 는 충족 판정 시점의 신청 인원(distinct 참여자 수 —
 * 다슬롯 참여자는 1명) 스냅샷이다.
 */
public record BuncheolFullEvent(Long buncheolId, long applicantCount) {}
