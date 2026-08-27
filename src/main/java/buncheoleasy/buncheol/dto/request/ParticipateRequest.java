package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 분철 참여 신청 요청. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이므로 {@code buncheolMemberId} 로 슬롯 하나를 지정한다. 오픈 이벤트 운영
 * 정책으로 분철당 활성 참여는 1건만 허용한다 (취소·만료된 참여는 재참여 가능).
 *
 * @param refundAccount <b>무시한다.</b> 환불 계좌는 서버가 마이페이지 정산 계좌에서 읽는다 — 계좌 입력 경로가 마이페이지 하나뿐이라 요청으로
 *     받으면 왕복만 늘고 화면에 보이는 계좌와 저장되는 계좌가 갈릴 수 있다. 구버전 클라이언트 호환을 위해 필드만 남겼고, 클라이언트 배포 후 제거한다.
 * @param participationCode 코드 참여 슬롯({@code CODE_ONLY})에 참여할 때 제출하는 코드. 선착순 슬롯에 보내면 거부한다.
 */
public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    RefundAccountRequest refundAccount,
    @Size(max = 32) String participationCode) {

  public ParticipateRequest(final Long buncheolMemberId, final Long shippingAddressId) {
    this(buncheolMemberId, shippingAddressId, null, null);
  }

  public ParticipateRequest(
      final Long buncheolMemberId, final Long shippingAddressId, final String participationCode) {
    this(buncheolMemberId, shippingAddressId, null, participationCode);
  }
}
