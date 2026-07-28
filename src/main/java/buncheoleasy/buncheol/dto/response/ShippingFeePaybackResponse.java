package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import java.time.Instant;

/**
 * 참여 목록/상세에 내려주는 배송비 환급(배송비 돌려받기) 상태. {@code status} 는 저장값이 아니라 이벤트 대상·배송 완료·신청 마감을 종합한 파생
 * 값이다(ShippingFeePaybackPolicy). 비대상(NONE)이어도 항상 내려 프론트 분기를 단순하게 한다. {@code submitDeadline} 은 신청 마감
 * 시각(배송 완료 시각 + 신청 가능 일수)으로, 이벤트 비대상이거나 마감 미적용(배송 완료 전 등)이면 null — 값이 있을 때만 "언제까지 신청" 안내를 그린다.
 * {@code amount} 는 신청 전에는 null 이고, 신청 후에는 신청 시점 배송비 스냅샷이다. {@code refundAccount} 는 환급을 입금받을
 * 계좌(참여 시 등록한 환불계좌)로, 돌려받기 시트에 표시한다.
 */
public record ShippingFeePaybackResponse(
    PaybackStatus status,
    Instant submitDeadline,
    String tweetUrl,
    Instant requestedAt,
    Instant completedAt,
    String rejectReason,
    Long amount,
    RefundAccountResponse refundAccount) {

  public static ShippingFeePaybackResponse of(
      final Participation participation,
      final PaybackStatus derivedStatus,
      final Instant submitDeadline) {
    return new ShippingFeePaybackResponse(
        derivedStatus,
        submitDeadline,
        participation.getPaybackTweetUrl(),
        participation.getPaybackRequestedAt(),
        participation.getPaybackCompletedAt(),
        participation.getPaybackRejectReason(),
        participation.getPaybackAmount(),
        RefundAccountResponse.from(participation.getRefundAccount()));
  }
}
