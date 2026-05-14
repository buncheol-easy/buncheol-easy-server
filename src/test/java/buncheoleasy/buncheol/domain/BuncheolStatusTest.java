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

    @Test
    void RECRUITING_상태에서만_취소_가능하다() {
      assertThat(BuncheolStatus.RECRUITING.isCancellable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING"},
        mode = EnumSource.Mode.EXCLUDE)
    void RECRUITING_외_상태에서는_취소_불가하다(BuncheolStatus status) {
      assertThat(status.isCancellable()).isFalse();
    }
  }
}
