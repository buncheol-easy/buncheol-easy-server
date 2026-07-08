package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 참여 신청이 접수됨(멤버 슬롯 점유, 입금 대기 시작). 운영자가 입금 기한 내에 입금확인할 수 있도록 슬랙 채널로 알린다. 한 참여 요청의 슬롯 묶음이 이벤트 한 건이다.
 */
public record ParticipationCreatedEvent(List<Long> participationIds) {}
