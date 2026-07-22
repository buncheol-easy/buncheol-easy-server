package buncheoleasy.buncheol.application.participation;

/**
 * 참여 신청이 접수됨(멤버 슬롯 점유, 입금 대기 시작). 운영자가 입금 기한 내에 확인·입금확인할 수 있도록 슬랙 채널로 알린다. 참여 1건 = 멤버 슬롯
 * 1개(단일 선택 정책)이므로 이벤트도 참여 한 건이 한 건이다.
 */
public record ParticipationCreatedEvent(Long participationId) {}
