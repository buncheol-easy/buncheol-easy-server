package buncheoleasy.buncheol.application.participation;

import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;
import java.util.List;

/**
 * 참여 신청 결과. 한 번에 점유한 참여 ID 목록과, 묶음 전체로 입금할 총액(멤버 금액 합 + 배송비 1회)·입금 만료 시각·개최자 계좌를 함께 전달한다.
 */
public record ParticipateResult(
    List<Long> participationIds, long amount, Instant dueAt, BankAccount hostAccount) {}
