package buncheoleasy.buncheol.application.participation;

import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;
import java.util.List;

/**
 * 참여 신청 결과. 생성된 참여 ID 와 입금할 총액(멤버 금액 + 배송비)·입금 만료 시각·개최자 계좌를 함께 전달한다.
 *
 * <p>{@code totalAmount} 는 배송비 포함 입금액이라, 굿즈 가격만 담는 {@link
 * buncheoleasy.buncheol.domain.participation.Participation#getAmount()} 와 의미가 다르다. 다중 슬롯이면
 * <b>합산</b>이다 — 배송비는 첫 슬롯에만 붙으므로 슬롯 금액을 단순히 곱하면 맞지 않는다.
 *
 * <p>{@code participationId} 는 <b>첫 슬롯</b>이고 {@code participationIds} 가 전체다. 단수 필드를 남긴 것은
 * 구버전 클라이언트 호환 때문이다.
 */
public record ParticipateResult(
    Long participationId,
    List<Long> participationIds,
    long totalAmount,
    Instant dueAt,
    BankAccount hostAccount) {

  public static ParticipateResult single(
      final Long participationId,
      final long totalAmount,
      final Instant dueAt,
      final BankAccount hostAccount) {
    return new ParticipateResult(
        participationId, List.of(participationId), totalAmount, dueAt, hostAccount);
  }
}
