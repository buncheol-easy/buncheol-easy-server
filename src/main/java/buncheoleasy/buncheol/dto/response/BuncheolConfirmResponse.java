package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.BuncheolConfirmResult;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

/** C2C 성사 확정 응답. 일괄 입금 기한과 입금 대기로 전이된 참여 수를 내려준다. */
public record BuncheolConfirmResponse(
    Long buncheolId, BuncheolStatus status, Instant paymentDueAt, int awaitingCount) {

  public static BuncheolConfirmResponse from(final BuncheolConfirmResult result) {
    return new BuncheolConfirmResponse(
        result.buncheolId(), result.status(), result.paymentDueAt(), result.awaitingCount());
  }
}
