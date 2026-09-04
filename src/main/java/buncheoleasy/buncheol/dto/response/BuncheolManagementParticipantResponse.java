package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.BundleReleasability;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 운영자(개최자) 관리 화면의 참여자 1건. 활성 참여 전체를 노출하며, (확정 시) 배송 스냅샷을 포함한다. {@code paymentSentAt} 은
 * C2C "보냈어요" 마킹 시각 — 개최자가 통장 대조 우선순위를 잡는 근거다.
 *
 * <p>{@code bundleId} 는 이 참여가 속한 묶음이고 {@code participantId} 는 그 묶음의 주인이다. 개최자 화면이 슬롯을
 * <b>묶음 1행</b>으로 접고 묶음 단위 조작(「제외」 등)을 부르려면 둘 다 필요하다 — 묶음 id 만으로는 같은 사람의 여러
 * 묶음(추가 모집)을 한 사람 아래로 모을 수 없다.
 *
 * <p><b>{@code participantId}(= {@code users.id}) 를 그대로 내리는 것은 의도된 결정이다.</b> 이 레포에서 유저 PK 를
 * 응답에 싣는 첫 사례라 근거를 남긴다 — 이 화면은 이미 같은 사람의 <b>예금주 실명</b>({@code depositorName})을 항상
 * 내려준다(통장 대조가 이 필드의 존재 이유다). 개최자는 자기 분철의 참여자만 볼 수 있고, 그 범위에서 실명이 이미 전역·
 * 사실상 불변인 식별자로 기능하므로 {@code users.id} 가 더하는 식별력이 없다. 응답 범위 안에서만 유효한 그룹핑 키를
 * 따로 만드는 대안은, 같은 식별력을 얻으면서 화면·서버 양쪽에 새 개념을 하나 더 들이는 비용만 남는다.
 *
 * <p>⚠️ 미연결 참여는 {@code bundleId} 가 {@code null} 일 수 있다. <b>null 끼리 묶으면 안 된다</b> — 화면이
 * {@code groupBy(bundleId)} 를 그대로 돌리면 <b>서로 다른 사람의 슬롯이 한 행으로 합쳐지고</b>, 그 행에서 「제외」를
 * 누르면 남의 슬롯까지 걸린다. null 인 행은 묶지 말고 슬롯 단위로 분리해 그린다.
 *
 * <p>{@code releasability} 는 이 참여가 속한 <b>묶음</b>의 「제외」 가능 여부다 — 슬롯 행마다 실리지만 값은 묶음
 * 단위다. 「제외」 게이트와 <b>같은 판정</b>을 내려줘 "버튼은 있는데 누르면 409" 가 생기지 않게 한다. 화면은
 * {@code RELEASABLE} 이 아니면 버튼을 비활성하고 사유를 표기한다. 미연결 참여는 {@code null}.
 * ⚠️ 값이 묶음 단위라 <b>취소된 행에도 {@code RELEASABLE} 이 실릴 수 있다</b>(같은 묶음에 활성 슬롯이
 * 남아 있고 기한이 지난 경우). 「제외」 버튼은 <b>활성 행에만</b> 그린다.
 *
 * <p>{@code confirmTarget} 은 이 슬롯이 <b>묶음 입금확인의 대상</b>인지다. 화면은 묶음 확인 API 에
 * {@code expectedSlotIds} 로 <b>이 값이 true 인 슬롯만</b> 실어야 한다 — 서버가 확인 대상을 같은 판정으로
 * 내려주므로 화면이 상태 문자열을 해석할 필요가 없고, 확인 대상 상태가 늘어도 양쪽이 갈리지 않는다.
 * 취소분·확정분까지 실으면 <b>영원히 409</b> 가 난다({@code BUNDLE_SLOTS_CHANGED}).
 *
 * <p><b>계좌 노출 범위</b>(docs/70 결정 21) — 개최자가 통장을 대조하는 데 필요한 것은 <b>입금자명뿐</b>이므로 평시에는 {@code
 * depositorName} 만 내리고 {@code refundAccount} 는 {@code null} 이다. 계좌번호는 <b>개최자가 실제로 환불해야 하는 건</b>, 즉
 * 취소분 중 <b>개최자가 입금확인한</b> 건에만 채운다 — 직거래라 환불 주체가 개최자다.
 * 「보냈어요」 마킹은 참여자 자기신고라 근거가 되지 않는다(안 낸 사람이 눌러도 찍힌다).
 *
 * <p>{@code amount} 는 <b>배송비를 포함한 입금 총액</b>이고 {@code shippingFee} 는 그중 배송비다. 배송비는 <b>묶음당 1회</b>라
 * 같은 묶음의 두 번째 슬롯은 0 이고, 참여자별 총액이 서로 달라진다 — 합계만 보면 개최자가 그 차이를 설명할 수 없다 (docs/53 Q-22).
 * ⚠️ 성사 확정 후 추가 모집은 <b>새 묶음</b>이라 같은 사람의 슬롯 두 개가 모두 >0 일 수 있다.
 */
public record BuncheolManagementParticipantResponse(
    Long participationId,
    Long bundleId,
    Long participantId,
    String participantNickname,
    Long buncheolMemberId,
    String memberName,
    String depositorName,
    long amount,
    long shippingFee,
    ParticipationStatus status,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    Instant paymentSentAt,
    BundleReleasability releasability,
    boolean confirmTarget) {}
