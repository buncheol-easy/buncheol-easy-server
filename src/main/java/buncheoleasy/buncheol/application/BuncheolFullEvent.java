package buncheoleasy.buncheol.application;

/**
 * C2C 모집 정원 최초 충족 — 신청으로 전 멤버 슬롯이 활성 참여로 채워짐 (마킹 CAS 로 분철당 1회만 발행). 커밋 후 개최자에게 진행 확정 독촉
 * 알림톡 발송을 트리거한다. {@code applicantCount} 는 충족 판정 시점의 신청 인원(distinct 참여자 수 — 다슬롯 참여자는 1명) 스냅샷이다.
 */
public record BuncheolFullEvent(Long buncheolId, long applicantCount) {}
