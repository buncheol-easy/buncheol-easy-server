package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 진행 확정된 회원 개최 분철에 추가 신청이 들어왔다. 개최자에게 <b>신청 요청마다 1통</b> 알린다.
 *
 * <p>{@code participationIds} 는 <b>이번 요청이 만든 참여만</b> 싣는다 — 같은 사람이 수락 전 묶음에 멤버를 더하면 묶음 전체를
 * 읽을 때 앞 요청의 멤버가 다시 보여 두 번 신청한 것처럼 읽힌다.
 */
public record ExtraAppliedEvent(Long buncheolId, List<Long> participationIds) {}
