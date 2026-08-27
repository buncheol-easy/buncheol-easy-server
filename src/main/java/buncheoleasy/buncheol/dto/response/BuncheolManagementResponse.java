package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;
import java.util.List;

/**
 * 운영자(개최자) 분철 관리 화면 응답. 분철 기본 정보 + 입금확인 대상·확정 참여자 목록(환불계좌·배송 포함).
 *
 * @param cancelledParticipants 취소된 참여(환불 계좌 확인용). {@code participants} 와 분리해 내려 참여 수 집계에 섞이지 않게
 *     한다 — 슬롯을 점유하지 않는다.
 * @param openChatUrl 참여자 소통용 오픈채팅 링크(없으면 null). 개최자가 이 화면에서 바로 등록·수정하므로 현재 값이 필요하다.
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
    String openChatUrl) {}
