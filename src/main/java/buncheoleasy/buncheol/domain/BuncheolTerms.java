package buncheoleasy.buncheol.domain;

import java.time.Instant;

/**
 * 신청자가 보고 신청하는 분철의 거래 조건 — 마감·최소 인원·배송비·구매처. 개최 뒤에는 활성 신청이 하나도 없는 C2C 분철에서만 바꿀 수
 * 있다({@link BuncheolTermsEditability}).
 *
 * <p>값은 검증을 마친 것만 담는다({@link Buncheol#applyTermsChange}). 쓰기는 엔티티 더티체킹이 아니라 조건부 UPDATE 로 한다
 * ({@link BuncheolRepository#changeTermsIfNoApplicant}).
 */
public record BuncheolTerms(
    Instant deadline, int minHeadcount, ShippingFeePolicy shippingFeePolicy, String purchaseSite) {}
