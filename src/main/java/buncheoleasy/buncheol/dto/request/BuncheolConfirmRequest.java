package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 회원 개최 진행 확정 요청.
 *
 * <p>{@code cancelParticipations} 는 개최자가 확인 창에서 본 <b>빠질 참여 목록</b>이다 — 관리 응답 {@code participants[]}
 * 가운데 입금 대기·「보냈어요」인 참여를 그대로 담는다. 모두 입금 확인됐으면(옛 데이터, 정상 흐름에선 이미 진행 확정됨) 빈 목록이다.
 * 서버는 이 목록을 <b>대조에만</b> 쓰고 취소 대상은 분철 범위로 스스로 고른다 — 다르면 409(BCH-127)로 막아 화면을 새로 고치게
 * 한다. 상태까지 담는 이유는 「보냈어요」가 같은 참여의 상태만 바꾸기 때문이다(강조 없이 본 사람을 빼지 않게).
 *
 * <p>본문이 없거나 이 필드가 없으면 400 이다 — 옛 클라이언트의 「성사 확정」(본문 없는 같은 경로)이 진행 확정으로 읽히면 안 된다.
 */
public record BuncheolConfirmRequest(
    @NotNull @Valid List<@NotNull @Valid CancelParticipation> cancelParticipations) {

  /** 빠질 참여 하나. {@code status} 는 {@code AWAITING_PAYMENT} 또는 {@code PAYMENT_SENT}. */
  public record CancelParticipation(
      @NotNull Long participationId, @NotNull ParticipationStatus status) {}
}
