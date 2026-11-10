package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 묶음 입금 확인 결과.
 *
 * @param confirmedParticipationIds 이번에 확인된 참여 id(오름차순)
 * @param buncheolConfirmed 이 확인으로 미확인이 0 이 돼 분철이 진행 확정됐는가
 */
public record BundleConfirmResult(
    List<Long> confirmedParticipationIds, boolean buncheolConfirmed) {}
