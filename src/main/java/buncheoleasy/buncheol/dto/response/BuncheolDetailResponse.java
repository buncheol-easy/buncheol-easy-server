package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 분철 단건 상세 조회 응답.
 *
 * @param hostedByMe 호출 유저가 이 분철의 개최자인지 여부. 비로그인 호출이면 항상 false
 * @param myParticipation 비로그인 호출 시 null
 */
public record BuncheolDetailResponse(
    Long id,
    String title,
    String groupName,
    String purchaseSite,
    Instant deadline,
    String description,
    BuncheolStatus status,
    boolean hostedByMe,
    List<String> imageUrls,
    List<ShippingOptionResponse> shippingOptions,
    List<BuncheolMemberBidResponse> members,
    MyParticipationSummaryResponse myParticipation) {}
