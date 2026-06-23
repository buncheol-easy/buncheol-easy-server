package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BuncheolStatus 도메인 테스트")
class BuncheolStatusTest {

  @Nested
  @DisplayName("취소되지 않은 상태 집합 테스트")
  class NotCancelledTest {

    @Test
    void RECRUITING과_CONFIRMED만_포함한다() {
      assertThat(BuncheolStatus.notCancelled())
          .containsExactlyInAnyOrder(BuncheolStatus.RECRUITING, BuncheolStatus.CONFIRMED);
    }

    @Test
    void CANCELLED는_포함하지_않는다() {
      assertThat(BuncheolStatus.notCancelled()).doesNotContain(BuncheolStatus.CANCELLED);
    }
  }

  @Nested
  @DisplayName("설명 테스트")
  class DescriptionTest {

    @Test
    void 각_상태는_설명을_가진다() {
      assertThat(BuncheolStatus.RECRUITING.getDescription()).isEqualTo("모집중");
      assertThat(BuncheolStatus.CONFIRMED.getDescription()).isEqualTo("진행확정");
      assertThat(BuncheolStatus.CANCELLED.getDescription()).isEqualTo("취소");
    }
  }
}
