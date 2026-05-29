package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 개최자 분철 관리 화면 응답. 호스트 본인만 호출 가능하며, 옵션별 입찰 현황과 낙찰자 배송 정보(닉네임·전화번호 포함)를 모두 노출한다.
 *
 * @param optionCount 분철에 등록된 멤버 슬롯(옵션) 수
 * @param totalParticipationCount 분철 전체 참여 수 (ACTIVE_BID + AWAITING_PAYMENT + CONFIRMED 합계). 한 유저가
 *     여러 슬롯에 입찰할 수 있어 distinct 참여자 수와는 다를 수 있다
 */
public record BuncheolManagementResponse(
    Long id,
    String title,
    String groupName,
    String purchaseSite,
    BuncheolStatus status,
    Instant deadline,
    int optionCount,
    int totalParticipationCount,
    List<BuncheolManagementOptionResponse> options) {}
