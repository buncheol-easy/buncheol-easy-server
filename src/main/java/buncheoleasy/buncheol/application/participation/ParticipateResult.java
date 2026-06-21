package buncheoleasy.buncheol.application.participation;

import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;

/** 참여 신청 결과. 참여자가 입금할 개최자 계좌·총액과 입금 만료 시각을 함께 전달한다. */
public record ParticipateResult(
    Long participationId, long amount, Instant dueAt, BankAccount hostAccount) {}
