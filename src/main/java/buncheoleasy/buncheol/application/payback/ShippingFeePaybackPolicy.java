package buncheoleasy.buncheol.application.payback;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송비 환급(배송비 돌려받기) 대상·상태 파생 규칙의 단독 소유자. 저장하지 않는 {@link PaybackStatus#ELIGIBLE}/{@link
 * PaybackStatus#EXPIRED} 는 전부 여기서 계산한다 — 신청 검증(ShippingFeePaybackService)과 참여 목록/상세 응답이 같은 규칙을
 * 공유해야 신청 가능으로 보였는데 신청이 거절되는 불일치가 없다.
 *
 * <p>예외: 회원탈퇴 가드({@code ParticipationRepository#existsUnfinishedByParticipantId})는 파생 판정을 재계산하지 않고
 * 저장 상태(REQUESTED)만 본다 — 신청/재신청 창이 열려 있어도 탈퇴를 막지 않는 운영 정책이라 마감 계산이 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class ShippingFeePaybackPolicy {

  private final ShippingFeePaybackProperties properties;

  /** 이벤트 활성 여부. 비활성 환경에서 판정 재료 조회(0원 슬롯 등)를 건너뛰는 용도. */
  public boolean isEnabled() {
    return properties.enabled();
  }

  /**
   * 참여 단위 환급 대상 여부 = 이벤트 활성 + 0원 슬롯 참여 + 입금확인(CONFIRMED)된 참여. 0원 슬롯 분철은 운영진만 발행하므로 별도의 이벤트
   * 기간 판정은 두지 않는다. {@code amount} 는 점유 시점 스냅샷이라 이후 가격 수정에 흔들리지 않는다.
   */
  public boolean isEventTarget(final Participation participation) {
    return properties.enabled()
        && participation.getAmount() == 0
        && participation.getStatus() == ParticipationStatus.CONFIRMED;
  }

  /** 분철 단위(참여 전) 이벤트 대상 여부 — 목록 카드 배지용. 전 슬롯 0원 여부는 호출 측이 조회해 넘긴다. */
  public boolean isEventTargetBuncheol(final boolean allSlotsFree) {
    return properties.enabled() && allSlotsFree;
  }

  /**
   * 저장 상태 + 배송 진행 + 신청 마감을 종합한 파생 상태.
   *
   * <ul>
   *   <li>저장 상태가 NONE 이 아니면(신청 이력 있음) 저장 상태 그대로 — 이벤트 종료 후에도 확정 이력은 유지된다. 단, REJECTED 재신청도 마감의
   *       적용을 받아 마감이 지나면 EXPIRED 로 닫는다.
   *   <li>NONE 이면: 비대상 → NONE, 배송 완료 전 → NONE(아직 신청 불가), 배송 완료 후 → 마감 전 ELIGIBLE / 마감 후 EXPIRED.
   * </ul>
   */
  public PaybackStatus deriveStatus(
      final Participation participation, final Delivery delivery, final Instant now) {
    PaybackStatus stored = participation.getPaybackStatus();
    if (stored == PaybackStatus.REJECTED && isSubmitClosed(delivery, now)) {
      return PaybackStatus.EXPIRED;
    }
    if (stored != PaybackStatus.NONE) {
      return stored;
    }
    if (!isEventTarget(participation)) {
      return PaybackStatus.NONE;
    }
    if (!isDeliveryCompleted(delivery)) {
      return PaybackStatus.NONE;
    }
    return isSubmitClosed(delivery, now) ? PaybackStatus.EXPIRED : PaybackStatus.ELIGIBLE;
  }

  private boolean isDeliveryCompleted(final Delivery delivery) {
    return delivery != null && DeliveryStatus.finished().contains(delivery.getStatus());
  }

  /**
   * 유저에게 노출하는 신청 마감 시각. 이벤트 비대상 참여(유료 슬롯 등)는 null — 비대상인데 마감 안내가 그려지는 불일치를 막는다. 대상이어도
   * 마감 미적용(배송 미완료·기준 시각 없음)이면 null. 대상이면 저장된 신청 상태(REQUESTED/COMPLETED 등)와 무관하게 내려가므로, 표시
   * 여부는 프론트가 파생 status 와 조합해 판단한다. 마감 판정({@link #deriveStatus})과 같은 계산을 공유한다.
   */
  public Instant submitDeadline(final Participation participation, final Delivery delivery) {
    return isEventTarget(participation) ? submitDeadline(delivery) : null;
  }

  // 신청 마감 시각 = 마감 기준 시점 + submitWindowDays. 기준 시점은 임시로 배송 완료 시각(delivered_at, 없으면 received_at)
  // 이다 — 분철 마감/운송장 등록 기준 등으로 바꾸는 정책 확정 시 이 메서드만 수정한다. 배송 미완료·기준 시각 없음이면 마감을 적용하지
  // 않는다(유저에게 유리한 쪽). 완료 여부를 상태로도 명시 가드해, 상태 정정 등으로 "SHIPPING 인데 delivered_at 잔존" 같은
  // 조합이 생겨도 deriveStatus 의 배송 완료 게이트와 어긋나지 않는다.
  private Instant submitDeadline(final Delivery delivery) {
    if (!isDeliveryCompleted(delivery)) {
      return null;
    }
    Instant anchor =
        delivery.getDeliveredAt() != null ? delivery.getDeliveredAt() : delivery.getReceivedAt();
    if (anchor == null) {
      return null;
    }
    return anchor.plus(Duration.ofDays(properties.submitWindowDays()));
  }

  private boolean isSubmitClosed(final Delivery delivery, final Instant now) {
    Instant deadline = submitDeadline(delivery);
    return deadline != null && now.isAfter(deadline);
  }
}
