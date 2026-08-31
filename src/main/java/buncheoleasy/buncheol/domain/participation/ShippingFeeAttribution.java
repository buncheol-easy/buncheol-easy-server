package buncheoleasy.buncheol.domain.participation;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * 묶음 배송비를 어느 슬롯에 붙여 보여줄지 정한다 (docs/62 M-01 · docs/81 §2).
 *
 * <p><b>왜 필요한가.</b> 배송비는 <b>묶음당 1회</b>다(택배가 1개니까). 그래서 같은 사람이 슬롯 2개를 잡으면 첫 슬롯에만 붙고
 * 나머지는 0 으로 저장된다. 그런데 그 <b>첫 슬롯이 취소되면 배송비도 같이 죽는다</b> — 남은 슬롯은 0 인 채로 남아 참여자는
 * 상품값만 내고, 개최자는 택배비를 자기 돈으로 문다. staging 재현으로 확인했다(참여 232 취소 → 233 배송비 0).
 *
 * <p><b>규칙.</b>
 *
 * <ol>
 *   <li>활성 슬롯이 있으면 그중 <b>가장 먼저 만들어진 것</b>이 배송비를 진다.
 *   <li>활성 슬롯이 없으면(전부 취소) <b>가장 나중에 취소된 것</b>이 진다 — 개최자가 환불할 금액에 배송비가 포함돼야 한다.
 * </ol>
 *
 * <p><b>②가 "가장 먼저 만들어진 것"이 아닌 이유가 중요하다.</b> 저장된 배송비는 항상 묶음 <b>첫</b> 슬롯에 붙어 있으므로
 * ("가장 먼저"로 두면 = 저장값으로 되돌아간다), 취소가 시차를 두고 일어나면 <b>표시 금액이 되돌아간다</b>:
 *
 * <pre>
 *   t0 둘 다 활성   232: 13,000   233: 10,000
 *   t1 232 취소     232: 10,000   233: 13,000   ← 개최자가 232 에 10,000 환불
 *   t2 233 도 취소  232: 13,000   233: 10,000   ← 되돌아옴. 3,000 과다 환불 또는 미지급
 * </pre>
 *
 * "가장 나중에 취소된 것"(= 마지막까지 살아 있던 슬롯)으로 두면 t1 의 판정이 t2 에도 유지된다. <b>한 행의 금액은 한번
 * 정해지면 변하지 않는다</b> — 환불 체크리스트로 쓰이는 화면의 계약으로 이쪽이 맞다.
 *
 * <p>이 판정은 <b>읽는 시점에</b> 한다. 취소할 때 값을 옮기는 방법도 있지만 그러려면 취소 경로 4개(자발·만료·개최자 취소·분철
 * cascade)를 전부 고쳐야 하고 동시 취소에서 이관이 겹치거나 빠질 수 있다. 읽기로 정하면 <b>취소가 무엇을 하든 금액이 저절로
 * 맞고</b>, 데이터를 망가뜨릴 수 없다. 계좌 정본을 묶음으로 옮긴 P2-c 와 같은 방향이다.
 *
 * <p>🔴 <b>{@link #ofAllSlots} 에는 반드시 그 묶음의 슬롯을 빠짐없이 넘겨야 한다.</b> 일부만 넘기면 그 조각 안에서 carrier
 * 를 다시 뽑아 <b>배송비가 두 번 걷힌다</b>(다른 페이지가 원래 carrier 를 그대로 내므로). 페이지네이션·필터가 걸린 목록에
 * 직접 쓰면 안 된다 — 그런 호출부는 {@link ParticipationBundleDomainService#shippingFeeAttributionFor(Collection)}
 * 을 쓰면 형제 슬롯을 대신 읽어 준다.
 */
public final class ShippingFeeAttribution {

  private static final ShippingFeeAttribution EMPTY =
      new ShippingFeeAttribution(Map.of(), Map.of());

  private final Map<Long, ParticipationBundle> bundleById;
  private final Map<Long, Long> carrierParticipationIdByBundleId;

  private ShippingFeeAttribution(
      final Map<Long, ParticipationBundle> bundleById,
      final Map<Long, Long> carrierParticipationIdByBundleId) {
    this.bundleById = bundleById;
    this.carrierParticipationIdByBundleId = carrierParticipationIdByBundleId;
  }

  /** 판정 근거가 없을 때. 모든 참여가 저장된 배송비를 그대로 쓴다. */
  public static ShippingFeeAttribution empty() {
    return EMPTY;
  }

  /**
   * @param allSlots 물어볼 묶음들의 <b>모든</b> 슬롯 (취소분 포함). 🔴 불완전하면 이중 부과가 난다 — 클래스 javadoc 참고
   * @param bundleById {@link ParticipationBundleDomainService#findAllByParticipations} 결과
   */
  public static ShippingFeeAttribution ofAllSlots(
      final Collection<Participation> allSlots,
      final Map<Long, ParticipationBundle> bundleById) {
    Map<Long, Participation> activeOldest = new HashMap<>();
    Map<Long, Participation> cancelledLatest = new HashMap<>();
    for (Participation participation : allSlots) {
      Long bundleId = participation.getBundleId();
      // 저장 전 인스턴스가 섞이면 Map 키·비교가 깨진다. 미연결 행(배포선 창)도 여기서 걸러진다.
      if (bundleId == null || participation.getId() == null) {
        continue;
      }
      if (ParticipationStatus.active().contains(participation.getStatus())) {
        activeOldest.merge(bundleId, participation, ShippingFeeAttribution::olderCreated);
      } else {
        cancelledLatest.merge(bundleId, participation, ShippingFeeAttribution::laterCancelled);
      }
    }
    Map<Long, Long> carrierByBundleId = new HashMap<>();
    cancelledLatest.forEach((bundleId, p) -> carrierByBundleId.put(bundleId, p.getId()));
    // 활성 슬롯이 하나라도 있으면 취소분을 덮는다 — 취소분이 배송비를 지는 건 전부 취소됐을 때뿐이다.
    activeOldest.forEach((bundleId, p) -> carrierByBundleId.put(bundleId, p.getId()));
    return new ShippingFeeAttribution(bundleById, carrierByBundleId);
  }

  // createdAt 이 같으면 id 로 가른다. id 는 AUTO_INCREMENT 라 실무상 생성 순서와 같지만,
  // 규칙의 축은 "먼저 만들어진 것"이므로 createdAt 을 1순위로 둔다 (백필·이관 데이터에서 갈릴 수 있다).
  private static Participation olderCreated(final Participation a, final Participation b) {
    return Comparator.comparing(
                Participation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Participation::getId)
            .compare(a, b)
        <= 0
        ? a
        : b;
  }

  /**
   * 더 나중에 취소된 쪽. 취소 시각이 비어 있는 행(있을 수 없지만 방어)은 가장 오래 전에 취소된 것으로 본다.
   *
   * <p>⚠️ <b>동률이면 먼저 만들어진 쪽(작은 id)을 고른다.</b> 분철 취소 cascade 는 활성 슬롯 전부를 <b>같은
   * {@code now}</b> 로 한꺼번에 취소하므로 동률이 실제로 흔하다. 그때 큰 id 를 고르면, 취소 직전까지 배송비를 지고
   * 있던 슬롯(= 활성 중 가장 오래된 것)에서 배송비가 <b>이유 없이 옮겨간다</b> — 개최자가 보는 환불 금액이 취소
   * 순간에 두 행 사이를 오간다. 작은 id 를 고르면 취소 전후로 같은 행이 배송비를 진다.
   */
  private static Participation laterCancelled(final Participation a, final Participation b) {
    int byCancelledAt =
        Comparator.comparing(
                (Participation p) ->
                    p.getCancelledAt() == null ? Instant.MIN : p.getCancelledAt())
            .compare(a, b);
    if (byCancelledAt != 0) {
      return byCancelledAt > 0 ? a : b;
    }
    return a.getId() <= b.getId() ? a : b;
  }

  /** 이 참여가 화면에 표시해야 할 배송비. */
  public long shippingFeeOf(final Participation participation) {
    Long bundleId = participation.getBundleId();
    // ⚠️ 맵을 조회하기 전에 걸러야 한다 — Map.of() 같은 불변 맵은 null 키 조회에서 NPE 다.
    if (bundleId == null) {
      return participation.getShippingFee();
    }
    ParticipationBundle bundle = bundleById.get(bundleId);
    Long carrierId = bundle == null ? null : carrierParticipationIdByBundleId.get(bundle.getId());
    // 미연결 참여(배포선 창)거나 그 묶음이 판정 대상이 아니면 저장값을 그대로 쓴다.
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
