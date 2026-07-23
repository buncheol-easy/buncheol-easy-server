package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import java.time.Instant;

/**
 * 참여 목록/상세에 내려주는 배송비 환급(배송비 돌려받기) 상태. {@code status} 는 저장값이 아니라 이벤트 대상·배송 완료·신청 마감을 종합한 파생
 * 값이다(ShippingFeePaybackPolicy). 비대상(NONE)이어도 항상 내려 프론트 분기를 단순하게 한다. {@code amount} 는 신청 전에는
 * null 이고, 신청 후에는 신청 시점 배송비 스냅샷이다.
 */
public record ShippingFeePaybackResponse(
    PaybackStatus status,
    String tweetUrl,
    Instant requestedAt,
    Instant completedAt,
    String rejectReason,
    Long amount) {

  public static ShippingFeePaybackResponse of(
      final Participation participation, final PaybackStatus derivedStatus) {
    return new ShippingFeePaybackResponse(
        derivedStatus,
        participation.getPaybackTweetUrl(),
        participation.getPaybackRequestedAt(),
        participation.getPaybackCompletedAt(),
        participation.getPaybackRejectReason(),
        participation.getPaybackAmount());
  }
}
