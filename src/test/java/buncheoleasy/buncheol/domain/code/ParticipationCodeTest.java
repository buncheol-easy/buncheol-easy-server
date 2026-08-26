package buncheoleasy.buncheol.domain.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

@DisplayName("ParticipationCode 단위 테스트")
class ParticipationCodeTest {

  private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
  private static final Long SLOT_ID = 101L;
  private static final Long OTHER_SLOT_ID = 102L;

  private ParticipationCode code() {
    return ParticipationCode.issue(
        "ABCD2345", 10L, SLOT_ID, "@supporter", NOW.plus(Duration.ofHours(48)), NOW);
  }

  private static void setField(final Object target, final String name, final Object value) {
    Field field = ReflectionUtils.findField(target.getClass(), name);
    ReflectionUtils.makeAccessible(field);
    ReflectionUtils.setField(field, target, value);
  }

  @Nested
  @DisplayName("발급 테스트")
  class IssueTest {

    @Test
    void 유효기한이_현재보다_과거면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  ParticipationCode.issue(
                      "ABCD2345", 10L, SLOT_ID, null, NOW.minusSeconds(1), NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_EXPIRY_INVALID);
    }

    @Test
    void 코드가_비어_있으면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  ParticipationCode.issue(
                      " ", 10L, SLOT_ID, null, NOW.plus(Duration.ofHours(1)), NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("사용 가능 판정 테스트")
  class RedeemabilityTest {

    @Test
    void 바인딩된_슬롯이고_기한_내면_사용_가능하다() {
      assertThat(code().redeemability(SLOT_ID, NOW)).isEqualTo(CodeRedeemability.REDEEMABLE);
    }

    @Test
    void 다른_슬롯이면_슬롯_불일치다() {
      assertThat(code().redeemability(OTHER_SLOT_ID, NOW))
          .isEqualTo(CodeRedeemability.SLOT_MISMATCH);
    }

    @Test
    void 슬롯_불일치를_만료보다_먼저_판정한다() {
      ParticipationCode expired = code();
      Instant afterExpiry = NOW.plus(Duration.ofHours(49));

      assertThat(expired.redeemability(OTHER_SLOT_ID, afterExpiry))
          .isEqualTo(CodeRedeemability.SLOT_MISMATCH);
    }

    @Test
    void 기한이_지나면_만료다() {
      assertThat(code().redeemability(SLOT_ID, NOW.plus(Duration.ofHours(48))))
          .isEqualTo(CodeRedeemability.EXPIRED);
    }

    @Test
    void 폐기된_코드는_폐기로_판정한다() {
      ParticipationCode revoked = code();
      setField(revoked, "revokedAt", NOW);

      assertThat(revoked.redeemability(SLOT_ID, NOW)).isEqualTo(CodeRedeemability.REVOKED);
    }

    @Test
    void 사용된_코드는_사용됨으로_판정한다() {
      ParticipationCode used = code();
      setField(used, "usedAt", NOW);

      assertThat(used.redeemability(SLOT_ID, NOW)).isEqualTo(CodeRedeemability.ALREADY_USED);
    }

    @Test
    void 기한_내_미사용_미폐기면_사용_가능하다() {
      assertThat(code().isUsable(NOW)).isTrue();
    }

    @Test
    void 기한이_지나면_사용_가능하지_않다() {
      assertThat(code().isUsable(NOW.plus(Duration.ofHours(48)))).isFalse();
    }

    @Test
    void 만료돼도_폐기_전이면_미사용_코드로_남는다() {
      assertThat(code().isOutstanding()).isTrue();
    }

    @Test
    void 폐기되면_미사용_코드가_아니다() {
      ParticipationCode revoked = code();
      setField(revoked, "revokedAt", NOW);

      assertThat(revoked.isOutstanding()).isFalse();
    }
  }
}
