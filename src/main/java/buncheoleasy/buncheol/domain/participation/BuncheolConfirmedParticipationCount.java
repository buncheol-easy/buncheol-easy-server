package buncheoleasy.buncheol.domain.participation;

/**
 * 분철별 입금확인(CONFIRMED) 참여 수 집계 — 개최 목록의 취소 가능 여부 판정용 (docs/56 S-2).
 *
 * <p>admin 컨텍스트의 {@code admin.domain.payment.BuncheolConfirmedCount} 와 JPQL 까지 동일하지만 <b>의도적으로
 * 별개</b>다(컨텍스트 분리). 한쪽의 "확정" 정의가 바뀌어도 다른 쪽이 따라가지 않으므로, 정의를 바꿀 때는 양쪽을 함께 본다.
 */
public record BuncheolConfirmedParticipationCount(Long buncheolId, long count) {}
