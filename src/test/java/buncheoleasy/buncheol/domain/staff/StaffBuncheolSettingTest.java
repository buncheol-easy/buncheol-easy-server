package buncheoleasy.buncheol.domain.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StaffBuncheolSetting 단위 테스트")
class StaffBuncheolSettingTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
  private static final Instant DEADLINE = Instant.parse("2026-10-21T11:00:00Z");
  private static final Instant OPENS_AT = Instant.parse("2026-10-20T11:00:00Z");

  @Nested
  @DisplayName("만들기 테스트")
  class CreateTest {

    @Test
    void 지금_이후_마감_전이면_만든다() {
      StaffBuncheolSetting setting =
          StaffBuncheolSetting.create(BUNCHEOL_ID, OPENS_AT, DEADLINE, NOW);

      assertThat(setting.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(setting.getId()).isEqualTo(BUNCHEOL_ID);
      assertThat(setting.getOpensAt()).isEqualTo(OPENS_AT);
      assertThat(setting.isNew()).isTrue();
    }

    @Test
    void 지난_시각이면_BCH_129() {
      assertInvalid(NOW.minusSeconds(1));
    }

    @Test
    void 지금과_같은_시각이면_BCH_129() {
      assertInvalid(NOW);
    }

    @Test
    void 마감과_같은_시각이면_BCH_129() {
      assertInvalid(DEADLINE);
    }

    @Test
    void 마감_뒤면_BCH_129() {
      assertInvalid(DEADLINE.plusSeconds(1));
    }

    @Test
    void 오픈_시각이_없으면_BCH_129() {
      assertInvalid(null);
    }

    @Test
    void 초_아래는_버리고_저장한다() {
      StaffBuncheolSetting setting =
          StaffBuncheolSetting.create(BUNCHEOL_ID, OPENS_AT.plusMillis(700), DEADLINE, NOW);

      assertThat(setting.getOpensAt()).isEqualTo(OPENS_AT);
    }

    @Test
    void 초_아래를_버린_값이_지금보다_앞서면_BCH_129() {
      Instant now = NOW.plusMillis(300);

      assertThatThrownBy(
              () -> StaffBuncheolSetting.create(BUNCHEOL_ID, NOW.plusMillis(700), DEADLINE, now))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OPENS_AT_INVALID);
    }

    private void assertInvalid(final Instant opensAt) {
      assertThatThrownBy(() -> StaffBuncheolSetting.create(BUNCHEOL_ID, opensAt, DEADLINE, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OPENS_AT_INVALID);
    }
  }

  @Nested
  @DisplayName("오픈 전 판정 테스트")
  class IsBeforeOpenTest {

    private final StaffBuncheolSetting setting =
        StaffBuncheolSetting.create(BUNCHEOL_ID, OPENS_AT, DEADLINE, NOW);

    @Test
    void 정각_1ms_전이면_오픈_전이다() {
      assertThat(setting.isBeforeOpen(OPENS_AT.minus(Duration.ofMillis(1)))).isTrue();
    }

    @Test
    void 정각이면_열렸다() {
      assertThat(setting.isBeforeOpen(OPENS_AT)).isFalse();
    }

    @Test
    void 정각_뒤면_열렸다() {
      assertThat(setting.isBeforeOpen(OPENS_AT.plusSeconds(1))).isFalse();
    }
  }
}
