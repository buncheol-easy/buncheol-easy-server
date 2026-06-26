package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.participation.ParticipateResult;
import java.time.Instant;
import java.util.List;

/**
 * 참여 신청 결과. 한 번에 점유한 참여 ID 목록과 함께, 참여자가 입금할 개최자 계좌·총액(멤버 금액 합 + 배송비 1회)·입금 만료 시각을 내려준다.
 */
public record ParticipateResponse(
    List<Long> participationIds, long amount, Instant dueAt, HostAccountResponse hostAccount) {

  public static ParticipateResponse from(final ParticipateResult result) {
    return new ParticipateResponse(
        result.participationIds(),
        result.totalAmount(),
        result.dueAt(),
        HostAccountResponse.from(result.hostAccount()));
  }
}
