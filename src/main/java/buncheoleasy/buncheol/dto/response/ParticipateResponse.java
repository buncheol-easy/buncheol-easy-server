package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.participation.ParticipateResult;
import java.time.Instant;

/** 참여 신청 결과. 참여자가 입금할 개최자 계좌·총액과 입금 만료 시각을 함께 내려준다. */
public record ParticipateResponse(
    Long participationId, long amount, Instant dueAt, HostAccountResponse hostAccount) {

  public static ParticipateResponse from(final ParticipateResult result) {
    return new ParticipateResponse(
        result.participationId(),
        result.amount(),
        result.dueAt(),
        HostAccountResponse.from(result.hostAccount()));
  }
}
