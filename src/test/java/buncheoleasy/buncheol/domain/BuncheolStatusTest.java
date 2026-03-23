package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("BuncheolStatus 도메인 테스트")
class BuncheolStatusTest {

  @Nested
  @DisplayName("취소 가능 여부 테스트")
  class IsCancellableTest {

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "CLOSED", "GOODS_ORDERED", "SELLER_SHIPPING"})
    void 취소_가능_상태이면_true를_반환한다(BuncheolStatus status) {
      assertThat(status.isCancellable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"HOST_SHIPPING", "ALL_RECEIVED", "SETTLING", "SETTLED", "FINISHED", "CANCELLED"})
    void 취소_불가_상태이면_false를_반환한다(BuncheolStatus status) {
      assertThat(status.isCancellable()).isFalse();
    }
  }

  @Nested
  @DisplayName("상태 전이 가능 여부 테스트")
  class CanAdvanceToTest {

    @Test
    void CLOSED에서_GOODS_ORDERED로_전이_가능하다() {
      assertThat(BuncheolStatus.CLOSED.canAdvanceTo(BuncheolStatus.GOODS_ORDERED)).isTrue();
    }

    @Test
    void GOODS_ORDERED에서_SELLER_SHIPPING으로_전이_가능하다() {
      assertThat(BuncheolStatus.GOODS_ORDERED.canAdvanceTo(BuncheolStatus.SELLER_SHIPPING))
          .isTrue();
    }

    @Test
    void CLOSED에서_SELLER_SHIPPING으로_전이_불가하다() {
      assertThat(BuncheolStatus.CLOSED.canAdvanceTo(BuncheolStatus.SELLER_SHIPPING)).isFalse();
    }

    @Test
    void GOODS_ORDERED에서_GOODS_ORDERED로_전이_불가하다() {
      assertThat(BuncheolStatus.GOODS_ORDERED.canAdvanceTo(BuncheolStatus.GOODS_ORDERED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"CLOSED", "GOODS_ORDERED"},
        mode = EnumSource.Mode.EXCLUDE)
    void CLOSED와_GOODS_ORDERED_외_상태에서는_전이_불가하다(BuncheolStatus status) {
      for (BuncheolStatus target : BuncheolStatus.values()) {
        assertThat(status.canAdvanceTo(target)).isFalse();
      }
    }
  }
}
