package buncheoleasy.buncheol.application.participation;

import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;

/**
 * 참여 신청 결과. 생성된 참여 ID 와 입금할 총액(멤버 금액 + 배송비)·입금 만료 시각·개최자 계좌를 함께 전달한다.
 *
 * <p>{@code totalAmount} 는 배송비 포함 입금액이라, 굿즈 가격만 담는 {@link
 * buncheoleasy.buncheol.domain.participation.Participation#getAmount()} 와 의미가 다르다.
 */
public record ParticipateResult(
    Long participationId, long totalAmount, Instant dueAt, BankAccount hostAccount) {}
