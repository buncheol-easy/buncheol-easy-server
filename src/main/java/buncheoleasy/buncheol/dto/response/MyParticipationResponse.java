package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.List;

/**
 * 마이페이지 참여 목록 항목. 목록 화면 렌더링에 필요한 분철 썸네일·배송옵션·배송 스냅샷을 함께 내려
 * 프론트가 참여 건마다 분철 상세/참여 상세를 추가 조회하지 않아도 되게 한다.
 * hostAccount 는 참여 상세와 동일하게 입금확인중(AWAITING_PAYMENT) 일 때만 노출한다.
 */
public record MyParticipationResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    int buncheolMemberCount,
    String memberName,
    long amount,
    ParticipationStatus participationStatus,
    ParticipationCancelReason cancelReason,
    BuncheolStatus buncheolStatus,
    Instant buncheolDeadline,
    Instant dueAt,
    Instant confirmedAt,
    String thumbnailUrl,
    List<ShippingOptionResponse> shippingOptions,
    HostAccountResponse hostAccount,
    MyParticipationDeliveryResponse delivery) {}
