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
    void 회원_개최_신청은_취소할_수_있다() {
      assertThat(
              ParticipationCancellability.of(
                  participation(ParticipationStatus.APPLIED, BEFORE_FINALIZE), c2c()))
          .isEqualTo(ParticipationCancellability.CANCELLABLE);
    }
  }

  @Nested
  @DisplayName("취소 차단 구간")
  class BlockedTest {

    // 입금 단계가 시작되면 스스로 빠질 수 없다 — 옛 추가 모집분(입금 수집 중 생성)도 같다.
    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"AWAITING_PAYMENT", "PAYMENT_SENT", "CONFIRMED", "CANCELLED"})
    void 회원_개최는_신청이_아니면_상태로_막는다(final ParticipationStatus status) {
      assertThat(
              ParticipationCancellability.of(participation(status, AFTER_FINALIZE), finalizedC2c()))
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_STATUS);
    }

    @Test
    void 자발_취소_CAS_의_상태_집합은_신청_하나다() {
      assertThat(ParticipationCancellability.cancellableStatuses())
          .containsExactly(ParticipationStatus.APPLIED);
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
