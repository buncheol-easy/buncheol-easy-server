package buncheoleasy.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
  PENDING("결제 대기"),
  CONFIRMING("승인 처리 중"),
  DONE("결제 완료"),
  FAILED("결제 실패");

  private final String description;
}
