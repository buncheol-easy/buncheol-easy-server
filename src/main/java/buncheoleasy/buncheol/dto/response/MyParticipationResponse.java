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
 *
 * <p>{@code amount} 는 배송비 포함 입금 총액이고, 이 참여에 부과된 배송비를 {@code shippingFee} 로 분리 노출해
 * 프론트가 멤버 가격(= amount - shippingFee)과 배송비를 나눠 그릴 수 있게 한다.
 *
 * <p>{@code payback} 은 오픈 이벤트 배송비 환급(배송비 돌려받기) 상태로, 비대상이어도 status=NONE 으로 항상 내려준다.
 *
 * <p>{@code refundHolder} 는 참여 시점에 박제된 환불계좌 예금주명이다. 자동 입금확인이 입금자명 일치로 매칭하므로
 * 프론트가 "이 이름으로 입금하세요" 안내에 쓴다. 프로필의 현재 계좌는 참여 후 변경됐을 수 있어 쓰면 안 된다.
 * hostAccount 와 같이 입금확인중(AWAITING_PAYMENT) 일 때만 노출한다.
 */
public record MyParticipationResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    int buncheolMemberCount,
    String memberName,
    long amount,
    long shippingFee,
    ParticipationStatus participationStatus,
    ParticipationCancelReason cancelReason,
    BuncheolStatus buncheolStatus,
    Instant buncheolDeadline,
    Instant dueAt,
    Instant confirmedAt,
    String thumbnailUrl,
    List<ShippingOptionResponse> shippingOptions,
    HostAccountResponse hostAccount,
    String refundHolder,
    MyParticipationDeliveryResponse delivery,
    ShippingFeePaybackResponse payback) {}
