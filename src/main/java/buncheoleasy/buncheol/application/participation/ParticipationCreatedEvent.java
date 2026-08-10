package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.FlowType;

/**
 * 참여 신청이 접수됨(멤버 슬롯 점유). 운영자가 확인할 수 있도록 슬랙 채널로 알린다. 참여 1건 = 멤버 슬롯 1개이므로 이벤트도 참여 한 건이 한 건이다.
 *
 * <p>{@code flowType} 으로 후속 처리가 갈린다 — 페이액션 주문 등록은 LEGACY(즉시 입금) 전용이고, C2C 는 개최자 계좌 직거래라 등록하지
 * 않는다 (docs/46 §3-2).
 */
public record ParticipationCreatedEvent(Long participationId, FlowType flowType) {}
