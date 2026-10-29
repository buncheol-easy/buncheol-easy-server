package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;

/**
 * 마이페이지 개최 목록 항목. 썸네일을 함께 내려 프론트가 분철 상세를 추가 조회하지 않아도 되게 한다.
 *
 * <p>{@code flowType} 은 검색 목록(`GET /v1/buncheols`)에만 있고 여기엔 빠져 있어 FE 가 상태로 플로우를 추정하던 것을 바로잡은 값이다
 * (docs/53 Q-13).
 *
 * <p>{@code cancellability} 는 개최자 취소 가능 여부와 사유다. 취소 API 게이트·CAS 와 <b>같은 판정</b>({@link
 * BuncheolHostCancellability#of})을 그대로 내려, 목록 카드의 삭제 버튼이 서버와 어긋나지 않게 한다 (docs/56 S-2). 입금 수집중이면
 * {@code NEEDS_STAFF} 다 — 삭제 대신 「운영진에게 알려 주세요」 안내를 띄운다.
 *
 * <p>{@code ended} 는 분철이 <b>완전히 끝났는지</b>다 — 진행확정이고, 미확정 참여가 없고, 확정 참여 전원의 택배가 편의점에 도착했다.
 * 확정 참여가 0명인 진행확정도 {@code true} 다. 판정은 회원탈퇴 가드와 같은 식이다({@code BuncheolRepository#findEndedIds}). 진행확정이 아니면 항상 {@code false} 다 —
 * 취소(CANCELLED)도 {@code false} 이므로, 화면의 「종료」 탭이 취소를 함께 담으려면 상태와 묶어서 판정해야 한다.
 *
 * <p>{@code confirmCheck} 는 관리 화면 응답과 같은 진행 확정 판정({@link ConfirmCheck})이다. 카드의 「진행 확정해 주세요」 배지는
 * {@code DUE_PASSED} 일 때만 띄운다 — 전원 입금은 마지막 묶음 입금 확인이 바로 진행 확정한다. {@code NO_CONFIRMED} 는 진행 확정
 * 버튼이 꺼져 있어 배지를 띄우지 않는다. 입금 수집중이 아니면 {@code null}.
 *
 * <p>{@code extraRecruitOpen} 은 추가 모집 스위치 값이다. 카드의 「추가 모집 중」 칩은 {@code ended} 가 false 일 때만 띄운다.
 *
 * <p>{@code pendingAcceptCount} 는 수락을 기다리는 추가 신청 <b>묶음</b> 수다(「수락해 주세요」 배지). 진행 확정 분철이 아니면 0 이다.
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
    boolean ended,
    ConfirmCheck confirmCheck,
    boolean extraRecruitOpen,
    long pendingAcceptCount) {}
