package buncheoleasy.buncheol.domain;

import java.time.Instant;

/**
 * 분철 수정 요청에 실린 거래 조건 칸(검증 전). 칸마다 null 이면 지금 값을 유지한다 — 조건 칸을 모르는 옛 클라이언트의 요청은 전부
 * null 이라 결과가 배포 전과 같다.
 *
 * <p>배송비 두 칸은 한 묶음이다. 둘 다 null 이면 지금 정책을 유지하고, 하나라도 오면 정책 전체를 바꾼다(안 보낸 쪽 = 지원하지 않는
 * 배송 방법). 개최 요청과 같은 해석이다.
 */
public record BuncheolTermsChange(
    Instant deadline,
    Integer minHeadcount,
    Integer gs25ShippingFee,
    Integer cuShippingFee,
    String purchaseSite) {

  public static BuncheolTermsChange none() {
    return new BuncheolTermsChange(null, null, null, null, null);
  }

  public boolean replacesShippingFee() {
    return gs25ShippingFee != null || cuShippingFee != null;
  }
}
