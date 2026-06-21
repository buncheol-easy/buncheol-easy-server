package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;

/**
 * 로그인 유저가 이 분철의 특정 멤버 슬롯에 가진 활성 참여 1건.
 *
 * @param participationId 참여 취소·상세 조회에 사용
 * @param status 입금확인중(AWAITING_PAYMENT) / 입금확인됨(CONFIRMED)
 */
public record MyParticipationItemResponse(
    Long participationId, Long buncheolMemberId, ParticipationStatus status) {}
