package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.user.domain.BankAccount;
import java.time.Instant;

/** 낙찰자(구매자) 결제 상세 응답. {@code hostAccount} 는 입금 대기/신고 단계에서만 채워진다(그 외 null). */
public record ParticipationPaymentDetailResponse(
    Long participationId,
    ParticipationStatus paymentStatus,
    long bidAmount,
    long shippingFee,
    long totalAmount,
    Instant paymentDueAt,
    HostAccountResponse hostAccount) {

  /** 개최자 계좌 정보. 계좌번호가 포함되므로 로그·외부 노출 시 주의한다. */
  public record HostAccountResponse(String bankName, String accountNumber, String accountHolder) {

    public static HostAccountResponse from(final BankAccount bankAccount) {
      return new HostAccountResponse(
          bankAccount.bank(), bankAccount.account(), bankAccount.holder());
    }
  }
}
