package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 개최자의 진행 확정 때 입금이 확인되지 않아 빠진 참여(취소 사유 {@code PAYMENT_NOT_CONFIRMED}). 분철 단위로 1번 발행한다 — 참여마다
 * 발행하면 다슬롯 참여자가 같은 안내를 슬롯 수만큼 받는다.
 */
public record PaymentNotConfirmedEvent(Long buncheolId, List<Long> participationIds) {}
