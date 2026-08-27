package buncheoleasy.admin.dto.response;

import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;

/**
 * 코드 발급 화면의 슬롯 1행.
 *
 * @param activeCode 가장 최근 미사용·미폐기 코드 (만료 포함). 없으면 null
 */
public record AdminBuncheolMemberResponse(
    Long buncheolMemberId,
    String memberName,
    long price,
    BuncheolMemberAccessType accessType,
    boolean taken,
    AdminParticipationCodeResponse activeCode) {}
