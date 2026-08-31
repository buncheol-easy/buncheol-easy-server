package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
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
 * <p>{@code bundleId} 는 이 참여가 속한 <b>묶음</b>이다 — 이체 1회 · 배송비 1회 · 택배 1개의 단위이고, <b>앞으로 추가될</b> 묶음 단위 API
 * (「보냈어요」 마킹 등)의 주소이기도 하다 — 이 PR 시점의 참여 API 는 아직 전부 슬롯 단위다. 배포선 창에서 생긴 미연결 참여는 {@code null} 일 수 있으므로
 * 프론트는 null 이면 슬롯 단위 경로로 폴백해야 한다.
 *
 * <p>{@code payback} 은 오픈 이벤트 배송비 환급(배송비 돌려받기) 상태로, 비대상이어도 status=NONE 으로 항상 내려준다.
 *
 * <p>{@code refundHolder} 는 참여 시점에 박제된 환불계좌 예금주명이다. 자동 입금확인이 입금자명 일치로 매칭하므로
 * 프론트가 "이 이름으로 입금하세요" 안내에 쓴다. 프로필의 현재 계좌는 참여 후 변경됐을 수 있어 쓰면 안 된다.
 * hostAccount 와 같이 입금확인중(AWAITING_PAYMENT) 일 때만 노출한다.
 *
 * <p>{@code cancellability} 는 자발 취소 가능 여부와 그 사유다. 취소 API 게이트와 <b>같은 판정</b>({@link
 * ParticipationCancellability#of})을 그대로 내려, 화면이 상태로 재판정하다 서버와 어긋나는 것을 막는다 (docs/56 S-1). 불리언 대신 사유까지
 * 실은 이유는 차단 구간마다 안내가 다르기 때문이다 — 보냈어요 이후(BCH-086)는 고객센터, 성사 확정 후(BCH-092)는 개최자 연락으로 보내야 한다.
 */
public record MyParticipationResponse(
    Long participationId,
    Long bundleId,
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
    ShippingFeePaybackResponse payback,
    FlowType flowType,
    Instant paymentSentAt,
    // 개최자 반려 시각. status=AWAITING_PAYMENT 와 함께 오면 참여 카드에 "입금 확인 안 됨 · 재확인 필요" 를
    // 강조 표시한다 (docs/53 Q-03). 재마킹 시 null 로 초기화된다.
    Instant paymentRejectedAt,
    String openChatUrl,
    ParticipationCancellability cancellability) {}
