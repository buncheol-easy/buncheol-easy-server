package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 배송비 환급 검수 처리 요청. {@code COMPLETE} 는 승인·입금완료를 한 번에 처리하고(중간 승인 단계 미사용), {@code REJECT} 는
 * {@code rejectReason} 이 필수다.
 */
public record AdminShippingFeePaybackActionRequest(
    @NotNull Action action, @Size(max = 200) String rejectReason) {

  public enum Action {
    COMPLETE,
    REJECT
  }
}
