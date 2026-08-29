package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ShippingFeeAttribution — 묶음 배송비를 어느 슬롯에 붙일지")
class ShippingFeeAttributionTest {

  private static final long BUNDLE_ID = 141L;
  private static final long BUNCHEOL_ID = 104L;
  private static final long PARTICIPANT_ID = 10L;
  private static final long SLOT_PRICE = 10_000L;
  private static final long SHIPPING_FEE = 3_000L;

  private static Participation participation(
      final long id, final long storedShippingFee, final ParticipationStatus status) {
    Participation participation =
        Participation.createApplied(
            BUNCHEOL_ID, id, PARTICIPANT_ID, 1L, SLOT_PRICE, storedShippingFee);
    ReflectionTestUtils.setField(participation, "id", id);
    ReflectionTestUtils.setField(participation, "bundleId", BUNDLE_ID);
    ReflectionTestUtils.setField(participation, "status", status);
    return participation;
  }

  private static Map<Long, ParticipationBundle> bundle() {
    ParticipationBundle bundle =
        ParticipationBundle.open(
            BUNCHEOL_ID,
            PARTICIPANT_ID,
            1L,
            SHIPPING_FEE,
            new RefundAccount("국민", "1234", "홍길동"),
            Instant.parse("2026-09-01T00:00:00Z"));
    ReflectionTestUtils.setField(bundle, "id", BUNDLE_ID);
    return Map.of(BUNDLE_ID, bundle);
  }

  @Nested
  @DisplayName("배송비를 진 슬롯이 취소되면")
  class WhenFeeCarrierCancelled {

    // 🔴 이 테스트가 이 클래스의 존재 이유다 (staging 재현: 참여 232 취소 → 233 배송비 0).
    @Test
    @DisplayName("남은 활성 슬롯이 배송비를 이어받는다")
    void remainingActiveSlotCarriesTheFee() {
      Participation cancelled = participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED);
      Participation remaining = participation(233L, 0L, ParticipationStatus.APPLIED);

      ShippingFeeAttribution attribution =
          ShippingFeeAttribution.of(List.of(cancelled, remaining), bundle());

      assertThat(attribution.shippingFeeOf(remaining)).isEqualTo(SHIPPING_FEE);
      assertThat(attribution.totalAmountOf(remaining)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
    }

    @Test
    @DisplayName("취소분은 배송비를 잃는다 — 택배가 계속 나가므로 그만큼은 환불 대상이 아니다")
    void cancelledSlotLosesTheFee() {
      Participation cancelled = participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED);
      Participation remaining = participation(233L, 0L, ParticipationStatus.APPLIED);

      ShippingFeeAttribution attribution =
          ShippingFeeAttribution.of(List.of(cancelled, remaining), bundle());

      assertThat(attribution.shippingFeeOf(cancelled)).isZero();
      assertThat(attribution.totalAmountOf(cancelled)).isEqualTo(SLOT_PRICE);
    }
  }

  @Test
  @DisplayName("활성 슬롯이 여럿이면 가장 먼저 만들어진 것 하나만 배송비를 진다 — 이중 부과 금지")
  void onlyOldestActiveSlotCarriesTheFee() {
    Participation first = participation(232L, SHIPPING_FEE, ParticipationStatus.APPLIED);
    Participation second = participation(233L, 0L, ParticipationStatus.APPLIED);
    Participation third = participation(234L, 0L, ParticipationStatus.APPLIED);

    ShippingFeeAttribution attribution =
        ShippingFeeAttribution.of(List.of(third, first, second), bundle());

    assertThat(attribution.shippingFeeOf(first)).isEqualTo(SHIPPING_FEE);
    assertThat(attribution.shippingFeeOf(second)).isZero();
    assertThat(attribution.shippingFeeOf(third)).isZero();
    // 묶음 전체로 걷히는 배송비는 정확히 1회분이다.
    assertThat(
            attribution.shippingFeeOf(first)
                + attribution.shippingFeeOf(second)
                + attribution.shippingFeeOf(third))
        .isEqualTo(SHIPPING_FEE);
  }

  @Test
  @DisplayName("전부 취소되면 가장 먼저 만들어진 취소분이 배송비를 진다 — 환불 금액에 포함돼야 한다")
  void allCancelledFallsBackToOldest() {
    Participation first = participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED);
    Participation second = participation(233L, 0L, ParticipationStatus.CANCELLED);

    ShippingFeeAttribution attribution =
        ShippingFeeAttribution.of(List.of(second, first), bundle());

    assertThat(attribution.totalAmountOf(first)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
    assertThat(attribution.totalAmountOf(second)).isEqualTo(SLOT_PRICE);
  }

  @Test
  @DisplayName("슬롯이 하나뿐이면(LEGACY) 저장된 값과 결과가 같다 — 기존 동작 무변경")
  void singleSlotBundleKeepsStoredValue() {
    Participation only = participation(232L, SHIPPING_FEE, ParticipationStatus.CONFIRMED);

    ShippingFeeAttribution attribution = ShippingFeeAttribution.of(List.of(only), bundle());

    assertThat(attribution.shippingFeeOf(only)).isEqualTo(only.getShippingFee());
    assertThat(attribution.totalAmountOf(only)).isEqualTo(only.getTotalAmount());
  }

  @Test
  @DisplayName("묶음 없는 참여(배포선 창)는 저장된 값을 그대로 쓴다")
  void unlinkedParticipationKeepsStoredValue() {
    Participation unlinked =
        Participation.createApplied(
            BUNCHEOL_ID, 1L, PARTICIPANT_ID, 1L, SLOT_PRICE, SHIPPING_FEE);
    ReflectionTestUtils.setField(unlinked, "id", 300L);
    ReflectionTestUtils.setField(unlinked, "status", ParticipationStatus.APPLIED);

    ShippingFeeAttribution attribution = ShippingFeeAttribution.of(List.of(unlinked), Map.of());

    assertThat(attribution.shippingFeeOf(unlinked)).isEqualTo(SHIPPING_FEE);
    assertThat(attribution.totalAmountOf(unlinked)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
  }

  @Test
  @DisplayName("형제 슬롯이 목록에 없으면 저장된 값을 쓴다 — 틀린 값을 새로 만들지 않는다")
  void partialListFallsBackToStoredValue() {
    Participation remaining = participation(233L, 0L, ParticipationStatus.APPLIED);

    // 묶음 맵은 있지만 목록에 이 참여가 없다 → 귀속 판정 근거 없음
    ShippingFeeAttribution attribution = ShippingFeeAttribution.of(List.of(), bundle());

    assertThat(attribution.shippingFeeOf(remaining)).isZero();
  }
}
