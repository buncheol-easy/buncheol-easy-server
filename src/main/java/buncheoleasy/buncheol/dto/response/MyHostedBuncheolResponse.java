package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;

/**
 * 마이페이지 개최 목록 항목. 썸네일을 함께 내려 프론트가 분철 상세를 추가 조회하지 않아도 되게 한다.
 *
 * <p>{@code flowType} 은 검색 목록(`GET /v1/buncheols`)에만 있고 여기엔 빠져 있어 FE 가 상태로 플로우를 추정하던 것을 바로잡은 값이다
 * (docs/53 Q-13).
 *
 * <p>{@code cancellability} 는 개최자 취소 가능 여부와 사유다. 취소 API 게이트·CAS 와 <b>같은 판정</b>({@link
 * BuncheolHostCancellability#of})을 그대로 내려, 목록 카드의 삭제 버튼이 서버와 어긋나지 않게 한다 (docs/56 S-2). 입금확인 건수를 그대로
 * 노출하지 않은 이유는 화면이 그 수를 따로 쓰지 않는데다, 건수를 내리면 "몇 건부터 막히는지" 를 화면이 다시 판정해야 해 Wave 2 에서 실제로 났던 어긋남
 * (docs/56 §21-4)을 다시 열기 때문이다.
 *
 * <p>{@code ended} 는 분철이 <b>완전히 끝났는지</b>다 — 진행확정이고, 미확정 참여가 없고, 확정 참여 전원의 택배가 편의점에 도착했다.
 * 확정 참여가 0명인 진행확정도 {@code true} 다. 판정은 회원탈퇴 가드와 같은 식이다({@code BuncheolRepository#findEndedIds}). 진행확정이 아니면 항상 {@code false} 다 —
 * 취소(CANCELLED)도 {@code false} 이므로, 화면의 「종료」 탭이 취소를 함께 담으려면 상태와 묶어서 판정해야 한다.
 */
public record MyHostedBuncheolResponse(
    Long buncheolId,
    String title,
    String groupName,
    BuncheolStatus status,
    Instant deadline,
    int memberSlotCount,
    long activeParticipationCount,
    Instant createdAt,
    String thumbnailUrl,
    FlowType flowType,
    BuncheolHostCancellability cancellability,
    boolean ended) {}
