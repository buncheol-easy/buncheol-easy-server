package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.participation.ParticipateResult;
import java.time.Instant;
import java.util.List;

/**
 * 참여 신청 결과. 참여자가 입금할 개최자 계좌·총액(멤버 금액 + 배송비)·입금 만료 시각을 내려준다.
 *
 * <p>{@code participationIds} 가 생성된 참여 전체이고, {@code participationId} 는 그중 <b>첫 슬롯</b>이다 —
 * 단수 필드는 구버전 클라이언트 호환을 위해 남겼다.
 *
 * <p>{@code amount} 는 다중 슬롯이면 <b>합산</b>이다. 배송비는 묶음당 1회라 첫 슬롯에만 붙으므로, 슬롯 금액에
 * 개수를 곱하면 맞지 않는다.
 */
public record ParticipateResponse(
    Long participationId,
    List<Long> participationIds,
    long amount,
    Instant dueAt,
    HostAccountResponse hostAccount) {

  public static ParticipateResponse from(final ParticipateResult result) {
    return new ParticipateResponse(
        result.participationId(),
        result.participationIds(),
        result.totalAmount(),
        result.dueAt(),
        HostAccountResponse.from(result.hostAccount()));
  }
}
