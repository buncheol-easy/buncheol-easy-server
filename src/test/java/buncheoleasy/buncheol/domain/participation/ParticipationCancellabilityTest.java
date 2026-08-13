package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("참여 자발 취소 가능 여부 판정 테스트 (docs/56 S-1)")
class ParticipationCancellabilityTest {

  private static final Long HOST_ID = 1L;
  private static final Instant FINALIZED_AT = Instant.parse("2026-05-14T12:00:00Z");
  private static final Instant BEFORE_FINALIZE = FINALIZED_AT.minus(1, ChronoUnit.HOURS);
  private static final Instant AFTER_FINALIZE = FINALIZED_AT.plus(1, ChronoUnit.HOURS);

  @Nested
  @DisplayName("취소 가능 구간")
  class CancellableTest {

    @Test
    void 신청_구간은_확정을_거쳤든_아니든_취소할_수_있다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.APPLIED, BEFORE_FINALIZE),
                  finalizedC2c()))
          .isEqualTo(ParticipationCancellability.CANCELLABLE);
    }

    /**
     * docs/46 §4.7-E1 — 입금 수집중 분철의 추가 모집은 APPLIED 를 거치지 않고 곧바로 AWAITING_PAYMENT 로 생성된다. 상태만 보고
     * 막으면 이 참여자는 신청하는 순간 24시간 잠겨 오신청조차 되돌릴 수 없다. 반드시 취소 가능이어야 한다.
     */
    @Test
    void 추가_모집으로_확정_이후에_생성된_입금_대기_참여는_취소할_수_있다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.AWAITING_PAYMENT, AFTER_FINALIZE),
                  finalizedC2c()))
          .isEqualTo(ParticipationCancellability.CANCELLABLE);
    }

    @Test
    void 아직_성사_확정되지_않은_분철의_입금_대기_참여는_취소할_수_있다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.AWAITING_PAYMENT, BEFORE_FINALIZE),
                  c2c()))
          .isEqualTo(ParticipationCancellability.CANCELLABLE);
    }
  }

  @Nested
  @DisplayName("취소 차단 구간")
  class BlockedTest {

    // H-09 본체. 이 판정을 지우면 확정 후 취소가 열려 빨개진다.
    @Test
    void 성사_확정을_거친_입금_대기_참여는_개최자_연락으로_보낸다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.AWAITING_PAYMENT, BEFORE_FINALIZE),
                  finalizedC2c()))
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_HOST_CONFIRM);
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"PAYMENT_SENT", "CONFIRMED", "CANCELLED"})
    void 보냈어요_이후_상태는_상태로_막는다(final ParticipationStatus status) {
      assertThat(ParticipationCancellability.of(participation(status, AFTER_FINALIZE), c2c()))
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_STATUS);
    }

    @Test
    void LEGACY_참여는_플로우로_막는다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.AWAITING_PAYMENT, AFTER_FINALIZE), legacy()))
          .isEqualTo(ParticipationCancellability.FLOW_NOT_SUPPORTED);
    }

    // 검사 순서가 계약이다 — LEGACY 의 입금확인 참여는 "지원하지 않는 요청"(BCH-091)이 아니라
    // "현재 상태에서는 취소 불가"(BCH-086)를 받아야 안내가 맞다.
    @Test
    void 상태_위반이_플로우_위반보다_먼저_판정된다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.CONFIRMED, AFTER_FINALIZE), legacy()))
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_STATUS);
    }
  }

  @Test
  void isCancellable_은_CANCELLABLE_에서만_true() {
    for (ParticipationCancellability value : ParticipationCancellability.values()) {
      assertThat(value.isCancellable())
          .isEqualTo(value == ParticipationCancellability.CANCELLABLE);
    }
  }

  private Participation participation(final ParticipationStatus status, final Instant createdAt) {
    Participation participation =
        Participation.create(
            10L,
            100L,
            1_000L,
            1L,
            50_000L,
            0L,
            RefundAccount.of("국민", "12345678", "홍길동"),
            FINALIZED_AT.plus(1, ChronoUnit.DAYS));
    setField(participation, "status", status);
    setField(participation, "createdAt", createdAt);
    return participation;
  }

  private Buncheol c2c() {
    return Buncheol.create(HOST_ID, params(FlowType.C2C), Instant.parse("2026-05-01T00:00:00Z"));
  }

  private Buncheol finalizedC2c() {
    Buncheol buncheol = c2c();
    setField(buncheol, "finalizedAt", FINALIZED_AT);
    return buncheol;
  }

  private Buncheol legacy() {
    return Buncheol.create(HOST_ID, params(FlowType.LEGACY), Instant.parse("2026-05-01T00:00:00Z"));
  }

  private BuncheolParams params(final FlowType flowType) {
    return new BuncheolParams(
        1L,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        Instant.parse("2026-12-31T00:00:00Z"),
        2,
        3_000,
        null,
        flowType,
        null);
  }

  private static void setField(final Object target, final String name, final Object value) {
    try {
      Field field = findField(target.getClass(), name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(final Class<?> type, final String name)
      throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
