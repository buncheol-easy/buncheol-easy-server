package buncheoleasy.admin.domain.payment;

/** 분철별 입금확인(CONFIRMED) 참여 수. 관리자 결제 목록에서 "최소 인원 도달 전" 표시용. */
public record BuncheolConfirmedCount(Long buncheolId, long count) {}
