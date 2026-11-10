package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;
import java.util.List;

/**
 * 운영자(개최자) 분철 관리 화면 응답. 분철 기본 정보 + 입금확인 대상·확정 참여자 목록(환불계좌·배송 포함).
 *
 * @param cancelledParticipants 취소된 참여(환불 계좌 확인용). {@code participants} 와 분리해 내려 참여 수 집계에 섞이지 않게
 *     한다 — 슬롯을 점유하지 않는다.
 * @param openChatUrl 참여자 소통용 오픈채팅 링크(없으면 null). 개최자가 이 화면에서 바로 등록·수정하므로 현재 값이 필요하다.
 * @param ended 분철이 완전히 끝났는지 — 개최 목록({@code MyHostedBuncheolResponse#ended})과 <b>같은 조회</b>로 판정한다. 목록
 *     카드와 관리 화면 머리가 다른 말을 하면 안 된다. 진행확정이 아니면 항상 {@code false}.
 * @param confirmCheck 회원 개최 진행 확정 판정. 진행 확정 API 게이트와 <b>같은 판정</b>({@link ConfirmCheck})이라 화면은
 *     {@code ALL_PAID}·{@code DUE_PASSED} 일 때만 진행 확정 버튼을 켠다. 전원 입금은 마지막 묶음 입금 확인이 바로 진행 확정하므로
 *     실제로 켜지는 것은 {@code DUE_PASSED} 다({@code ALL_PAID} 는 옛 데이터 대비). 입금 수집중이 아니면 {@code null}.
 */
public record BuncheolManagementResponse(
    Long id,
    String title,
    String groupName,
    String purchaseSite,
    BuncheolStatus status,
    Instant deadline,
    int minHeadcount,
    int memberCount,
    int confirmedCount,
    List<BuncheolManagementParticipantResponse> participants,
    List<BuncheolManagementParticipantResponse> cancelledParticipants,
    FlowType flowType,
    Instant paymentDueAt,
    String openChatUrl,
    boolean ended,
    ConfirmCheck confirmCheck) {}
