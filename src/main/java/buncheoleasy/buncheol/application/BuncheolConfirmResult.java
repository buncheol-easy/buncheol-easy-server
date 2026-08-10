package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

/** C2C 성사 확정 결과 (docs/46 §4.1). 일괄 입금 기한과 입금 대기로 전이된 참여 수를 함께 전달한다. */
public record BuncheolConfirmResult(
    Long buncheolId, BuncheolStatus status, Instant paymentDueAt, int awaitingCount) {}
