package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("거래 조건 수정 가능 여부 판정 테스트")
class BuncheolTermsEditabilityTest {

  private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
  private static final Instant BEFORE_DEADLINE = NOW.plusSeconds(3600);

  @Test
  void 모집중_마감_전_C2C_에_활성_신청이_없으면_바꿀_수_있다() {
    assertThat(
            BuncheolTermsEditability.of(
                BuncheolStatus.RECRUITING, FlowType.C2C, BEFORE_DEADLINE, NOW, false))
        .isEqualTo(BuncheolTermsEditability.EDITABLE);
  }

  // 신청자가 본 조건은 바꾸지 않는다 — 이 분기를 지우면 신청자가 있는 분철의 조건 칸이 열린다.
  @Test
  void 활성_신청이_하나라도_있으면_신청자로_막는다() {
    assertThat(
            BuncheolTermsEditability.of(
                BuncheolStatus.RECRUITING, FlowType.C2C, BEFORE_DEADLINE, NOW, true))
        .isEqualTo(BuncheolTermsEditability.BLOCKED_BY_APPLICANT);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void LEGACY_는_신청자와_무관하게_방식으로_막는다(final boolean hasActive) {
    assertThat(
            BuncheolTermsEditability.of(
                BuncheolStatus.RECRUITING, FlowType.LEGACY, BEFORE_DEADLINE, NOW, hasActive))
        .isEqualTo(BuncheolTermsEditability.BLOCKED_BY_FLOW);
  }

  // 마감 정각 포함 — Buncheol#validateRecruiting 과 같은 경계(deadline > now)다.
  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, -3600L})
  void 마감이_지났거나_정각이면_상태로_막는다(final long secondsUntilDeadline) {
    Instant deadline = NOW.plusSeconds(secondsUntilDeadline);

    assertThat(
            BuncheolTermsEditability.of(BuncheolStatus.RECRUITING, FlowType.C2C, deadline, NOW, false))
        .isEqualTo(BuncheolTermsEditability.BLOCKED_BY_STATUS);
  }

  @ParameterizedTest
  @EnumSource(
      value = BuncheolStatus.class,
      names = {"RECRUITING"},
      mode = EnumSource.Mode.EXCLUDE)
  void 모집중이_아니면_상태로_막는다(final BuncheolStatus status) {
    assertThat(BuncheolTermsEditability.of(status, FlowType.C2C, BEFORE_DEADLINE, NOW, false))
        .isEqualTo(BuncheolTermsEditability.BLOCKED_BY_STATUS);
  }

  // 상태 × 방식 × 마감 전후 × 활성 0/1 전 조합. 새 상태·방식이 생기면 위 개별 테스트가 아니라 이 표가 먼저 어긋난다.
  @Test
  void 전_조합에서_EDITABLE_은_모집중_마감_전_C2C_활성_0_하나뿐이다() {
    for (BuncheolStatus status : BuncheolStatus.values()) {
      for (FlowType flowType : FlowType.values()) {
        for (Instant deadline : new Instant[] {BEFORE_DEADLINE, NOW}) {
          for (boolean hasActive : new boolean[] {false, true}) {
            boolean expected =
                status == BuncheolStatus.RECRUITING
                    && flowType == FlowType.C2C
                    && deadline.isAfter(NOW)
                    && !hasActive;

            assertThat(
                    BuncheolTermsEditability.of(status, flowType, deadline, NOW, hasActive)
                        .isEditable())
                .as("%s / %s / deadline=%s / active=%s", status, flowType, deadline, hasActive)
                .isEqualTo(expected);
          }
        }
      }
    }
  }
}
