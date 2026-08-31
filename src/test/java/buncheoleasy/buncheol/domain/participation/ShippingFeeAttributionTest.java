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
    return participation(id, storedShippingFee, status, null);
  }

  private static Participation participation(
      final long id,
      final long storedShippingFee,
      final ParticipationStatus status,
      final Instant cancelledAt) {
    Participation participation =
        Participation.createApplied(
            BUNCHEOL_ID, id, PARTICIPANT_ID, 1L, SLOT_PRICE, storedShippingFee);
    ReflectionTestUtils.setField(participation, "id", id);
    ReflectionTestUtils.setField(participation, "bundleId", BUNDLE_ID);
    ReflectionTestUtils.setField(participation, "status", status);
    // createdAt 은 영속화 시점에 채워지므로 테스트에서 직접 심는다. id 순 = 생성 순으로 맞춘다.
    ReflectionTestUtils.setField(
        participation, "createdAt", Instant.parse("2026-08-29T00:00:00Z").plusSeconds(id));
    ReflectionTestUtils.setField(participation, "cancelledAt", cancelledAt);
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
          ShippingFeeAttribution.ofAllSlots(List.of(cancelled, remaining), bundle());

      assertThat(attribution.shippingFeeOf(remaining)).isEqualTo(SHIPPING_FEE);
      assertThat(attribution.totalAmountOf(remaining)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
    }

    @Test
    @DisplayName("취소분은 배송비를 잃는다 — 택배가 계속 나가므로 그만큼은 환불 대상이 아니다")
    void cancelledSlotLosesTheFee() {
      Participation cancelled = participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED);
      Participation remaining = participation(233L, 0L, ParticipationStatus.APPLIED);

      ShippingFeeAttribution attribution =
          ShippingFeeAttribution.ofAllSlots(List.of(cancelled, remaining), bundle());

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
        ShippingFeeAttribution.ofAllSlots(List.of(third, first, second), bundle());

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

  // 🔴 리뷰가 잡은 것 — 폴백을 "가장 먼저 만들어진 것"으로 두면 t1 에 옮겨간 배송비가 t2 에 되돌아온다.
  // 개최자가 t1 화면을 보고 환불한 뒤 t2 에 금액이 바뀌면 과다 환불 또는 미지급이 난다.
  @Test
  @DisplayName("시차를 두고 전부 취소돼도 한번 정해진 금액은 되돌아오지 않는다")
  void amountDoesNotRevertWhenRemainingSlotIsCancelledLater() {
    Instant t1 = Instant.parse("2026-08-29T10:00:00Z");
    Instant t2 = t1.plusSeconds(3600);
    Participation firstCancelled =
        participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED, t1);
    Participation stillActive = participation(233L, 0L, ParticipationStatus.APPLIED);

    // t1 — 232 만 취소된 시점
    ShippingFeeAttribution atT1 =
        ShippingFeeAttribution.ofAllSlots(List.of(firstCancelled, stillActive), bundle());
    assertThat(atT1.totalAmountOf(firstCancelled)).isEqualTo(SLOT_PRICE);
    assertThat(atT1.totalAmountOf(stillActive)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);

    // t2 — 233 도 취소된 시점. 두 행의 금액이 t1 과 같아야 한다.
    Participation laterCancelled =
        participation(233L, 0L, ParticipationStatus.CANCELLED, t2);
    ShippingFeeAttribution atT2 =
        ShippingFeeAttribution.ofAllSlots(List.of(firstCancelled, laterCancelled), bundle());
    assertThat(atT2.totalAmountOf(firstCancelled)).isEqualTo(SLOT_PRICE);
    assertThat(atT2.totalAmountOf(laterCancelled)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
  }

  // 분철 취소 cascade 는 활성 슬롯 전부를 같은 now 로 한꺼번에 취소한다 — 동률이 흔하다.
  @Test
  @DisplayName("한꺼번에 취소되면(cascade) 직전까지 배송비를 지던 슬롯이 그대로 진다")
  void cascadeCancelKeepsTheSameCarrier() {
    Instant cancelledAt = Instant.parse("2026-08-29T10:00:00Z");
    Participation active232 = participation(232L, SHIPPING_FEE, ParticipationStatus.APPLIED);
    Participation active233 = participation(233L, 0L, ParticipationStatus.APPLIED);
    ShippingFeeAttribution before =
        ShippingFeeAttribution.ofAllSlots(List.of(active232, active233), bundle());
    assertThat(before.totalAmountOf(active232)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);

    // 같은 시각에 둘 다 취소
    Participation dead232 =
        participation(232L, SHIPPING_FEE, ParticipationStatus.CANCELLED, cancelledAt);
    Participation dead233 = participation(233L, 0L, ParticipationStatus.CANCELLED, cancelledAt);
    ShippingFeeAttribution after =
        ShippingFeeAttribution.ofAllSlots(List.of(dead232, dead233), bundle());

    // 배송비가 이유 없이 옮겨가면 개최자의 환불 금액이 취소 순간에 두 행 사이를 오간다.
    assertThat(after.totalAmountOf(dead232)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
    assertThat(after.totalAmountOf(dead233)).isEqualTo(SLOT_PRICE);
  }

  @Test
  @DisplayName("슬롯이 하나뿐이면(LEGACY) 저장된 값과 결과가 같다 — 기존 동작 무변경")
  void singleSlotBundleKeepsStoredValue() {
    Participation only = participation(232L, SHIPPING_FEE, ParticipationStatus.CONFIRMED);

    ShippingFeeAttribution attribution = ShippingFeeAttribution.ofAllSlots(List.of(only), bundle());

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

    ShippingFeeAttribution attribution = ShippingFeeAttribution.ofAllSlots(List.of(unlinked), Map.of());

    assertThat(attribution.shippingFeeOf(unlinked)).isEqualTo(SHIPPING_FEE);
    assertThat(attribution.totalAmountOf(unlinked)).isEqualTo(SLOT_PRICE + SHIPPING_FEE);
  }

  // 🔴 리뷰가 잡은 것 — 이전 테스트는 <b>빈 목록</b>을 넘기고 isZero() 를 단언했는데 저장값도 0 이라 공허했다.
  // 진짜 위험한 것은 "일부만 있는 목록"이고, 그 경우 이 클래스는 조각 안에서 carrier 를 다시 뽑아 이중 부과를 낸다.
  // 그래서 계약은 "완전한 목록 필수"이고, 페이지네이션 호출부는 도메인 서비스의 진입점을 써야 한다.
  @Test
  @DisplayName("불완전한 목록을 넘기면 이중 부과가 난다 — 이 계약을 어기지 말 것")
  void partialListDoubleChargesTheFee() {
    Participation carrier = participation(232L, SHIPPING_FEE, ParticipationStatus.APPLIED);
    Participation other = participation(233L, 0L, ParticipationStatus.APPLIED);

    // 페이지 A: 232 만 보인다 → 232 가 carrier
    ShippingFeeAttribution pageA =
        ShippingFeeAttribution.ofAllSlots(List.of(carrier), bundle());
    // 페이지 B: 233 만 보인다 → 233 도 carrier 가 되어 버린다
    ShippingFeeAttribution pageB = ShippingFeeAttribution.ofAllSlots(List.of(other), bundle());

    assertThat(pageA.shippingFeeOf(carrier)).isEqualTo(SHIPPING_FEE);
    assertThat(pageB.shippingFeeOf(other)).isEqualTo(SHIPPING_FEE);
    // 합치면 배송비가 두 번 걷힌다 — 완전한 목록이었다면 SHIPPING_FEE 1회여야 한다.
    assertThat(pageA.shippingFeeOf(carrier) + pageB.shippingFeeOf(other))
        .isEqualTo(SHIPPING_FEE * 2);
  }

  @Test
  @DisplayName("판정 근거가 없으면(empty) 저장된 값을 그대로 쓴다")
  void emptyAttributionKeepsStoredValue() {
    Participation carrier = participation(232L, SHIPPING_FEE, ParticipationStatus.APPLIED);
    Participation other = participation(233L, 0L, ParticipationStatus.APPLIED);

    ShippingFeeAttribution attribution = ShippingFeeAttribution.empty();

    assertThat(attribution.shippingFeeOf(carrier)).isEqualTo(SHIPPING_FEE);
    assertThat(attribution.shippingFeeOf(other)).isZero();
  }
}
