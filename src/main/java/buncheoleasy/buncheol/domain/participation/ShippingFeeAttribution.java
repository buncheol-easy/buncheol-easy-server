package buncheoleasy.buncheol.domain.participation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 묶음 배송비를 어느 슬롯에 붙여 보여줄지 정한다 (docs/62 M-01 · docs/81 §2).
 *
 * <p><b>왜 필요한가.</b> 배송비는 <b>묶음당 1회</b>다(택배가 1개니까). 그래서 같은 사람이 슬롯 2개를 잡으면 첫 슬롯에만 붙고
 * 나머지는 0 으로 저장된다. 그런데 그 <b>첫 슬롯이 취소되면 배송비도 같이 죽는다</b> — 남은 슬롯은 0 인 채로 남아 참여자는
 * 상품값만 내고, 개최자는 택배비를 자기 돈으로 문다. staging 재현으로 확인했다(참여 232 취소 → 233 배송비 0).
 *
 * <p><b>규칙 한 줄.</b> 묶음의 배송비는 그 묶음의 <b>활성 슬롯 중 가장 먼저 만들어진 것</b>에 붙는다. 활성 슬롯이 하나도
 * 없으면(전부 취소) 전체 중 가장 먼저 만들어진 것에 붙는다 — 개최자가 환불할 금액에 배송비가 포함돼야 하기 때문이다.
 *
 * <p>이 판정은 <b>읽는 시점에</b> 한다. 취소할 때 값을 옮기는 방법도 있지만 그러려면 취소 경로 4개(자발·만료·개최자 취소·분철
 * cascade)를 전부 고쳐야 하고 동시 취소에서 이관이 겹치거나 빠질 수 있다. 읽기로 정하면 <b>취소가 무엇을 하든 금액이 저절로
 * 맞고</b>, 데이터를 망가뜨릴 수 없다. 계좌 정본을 묶음으로 옮긴 P2-c 와 같은 방향이다.
 *
 * <p>⚠️ {@link #of} 에 넘기는 목록은 <b>물어볼 묶음의 슬롯을 빠짐없이</b> 담아야 한다. 일부만 담기면 그 묶음은 판정 근거가
 * 없다고 보고 저장된 값을 그대로 쓴다(고쳐지지 않을 뿐 틀린 값을 새로 만들지는 않는다).
 */
public final class ShippingFeeAttribution {

  private final Map<Long, ParticipationBundle> bundleById;
  private final Map<Long, Long> carrierParticipationIdByBundleId;

  private ShippingFeeAttribution(
      final Map<Long, ParticipationBundle> bundleById,
      final Map<Long, Long> carrierParticipationIdByBundleId) {
    this.bundleById = bundleById;
    this.carrierParticipationIdByBundleId = carrierParticipationIdByBundleId;
  }

  /**
   * @param participations 물어볼 묶음들의 <b>모든</b> 슬롯 (취소분 포함)
   * @param bundleById {@link ParticipationBundleDomainService#findAllByParticipations} 결과
   */
  public static ShippingFeeAttribution of(
      final Collection<Participation> participations,
      final Map<Long, ParticipationBundle> bundleById) {
    Map<Long, Long> activeOldest = new HashMap<>();
    Map<Long, Long> anyOldest = new HashMap<>();
    for (Participation participation : participations) {
      Long bundleId = participation.getBundleId();
      if (bundleId == null) {
        continue;
      }
      anyOldest.merge(bundleId, participation.getId(), Math::min);
      if (ParticipationStatus.active().contains(participation.getStatus())) {
        activeOldest.merge(bundleId, participation.getId(), Math::min);
      }
    }
    // 활성 슬롯이 있으면 그쪽이 이기고, 없을 때만 취소분이 배송비를 진다.
    anyOldest.forEach(activeOldest::putIfAbsent);
    return new ShippingFeeAttribution(bundleById, activeOldest);
  }

  /** 이 참여가 화면에 표시해야 할 배송비. */
  public long shippingFeeOf(final Participation participation) {
    Long bundleId = participation.getBundleId();
    // ⚠️ 맵을 조회하기 전에 걸러야 한다 — 단건 조회 경로가 넘기는 Map.of() 는 불변 맵이라 null 키에 NPE 다.
    if (bundleId == null) {
      return participation.getShippingFee();
    }
    ParticipationBundle bundle = bundleById.get(bundleId);
    Long carrierId = bundle == null ? null : carrierParticipationIdByBundleId.get(bundle.getId());
    // 미연결 참여(배포선 창)거나 그 묶음의 슬롯이 목록에 없으면 판정 근거가 없다 — 저장값을 그대로 쓴다.
    if (carrierId == null) {
      return participation.getShippingFee();
    }
    return carrierId.equals(participation.getId()) ? bundle.getShippingFee() : 0L;
  }

  /**
   * 이 참여의 입금 총액. {@link Participation#getTotalAmount()} 과 달리 배송비를 <b>귀속 판정</b>으로 얹는다 — 저장된
   * 배송비를 그대로 더하면 위 문제가 그대로 화면에 나온다.
   */
  public long totalAmountOf(final Participation participation) {
    return participation.getAmount() + shippingFeeOf(participation);
  }
}
