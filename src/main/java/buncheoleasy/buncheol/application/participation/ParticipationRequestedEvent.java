package buncheoleasy.buncheol.application.participation;

/** 참여자가 분철에 참여함(멤버 슬롯 점유, AWAITING_PAYMENT). 개최자에게 입금확인 요청 알림. */
public record ParticipationRequestedEvent(Long participationId) {}
