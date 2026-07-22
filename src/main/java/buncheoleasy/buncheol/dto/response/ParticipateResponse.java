package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.participation.ParticipateResult;
import java.time.Instant;

/**
 * 참여 신청 결과. 생성된 참여 ID({@code participationId})와 함께, 참여자가 입금할 개최자 계좌·총액(멤버 금액 + 배송비)·입금 만료 시각을
 * 내려준다.
 */
public record ParticipateResponse(
    Long participationId, long amount, Instant dueAt, HostAccountResponse hostAccount) {

  public static ParticipateResponse from(final ParticipateResult result) {
    return new ParticipateResponse(
        result.participationId(),
        result.totalAmount(),
        result.dueAt(),
        HostAccountResponse.from(result.hostAccount()));
  }
}
