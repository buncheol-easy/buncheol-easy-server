package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("BundleReleasability — 「제외」 판정")
class BundleReleasabilityTest {

  private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
  private static final Instant PAST_DUE = NOW.minusSeconds(1);
  private static final Instant FUTURE_DUE = NOW.plusSeconds(3600);

  private static ParticipationBundle bundle(final Instant dueAt, final Instant closedAt) {
    ParticipationBundle bundle =
        ParticipationBundle.open(
            1L, 10L, 1L, 3_000L, new RefundAccount("국민", "1234", "홍길동"), dueAt, true);
    setField(bundle, "closedAt", closedAt);
    return bundle;
  }

  private static Participation slot(final ParticipationStatus status) {
    Participation participation = Participation.createApplied(1L, 101L, 10L, 1L, 10_000L, 0L);
    setField(participation, "status", status);
    return participation;
  }

  private static BundleReleasability of(
      final BuncheolStatus buncheolStatus,
      final ParticipationBundle bundle,
      final ParticipationStatus... statuses) {
    return BundleReleasability.of(
        buncheolStatus,
        bundle,
        Arrays.stream(statuses).map(BundleReleasabilityTest::slot).toList(),
        NOW);
  }

  @Test
  void 진행_확정_뒤_기한이_지난_입금_대기와_보냈어요는_뺄_수_있다() {
    assertThat(
            of(
                BuncheolStatus.CONFIRMED,
                bundle(PAST_DUE, null),
                ParticipationStatus.AWAITING_PAYMENT,
                ParticipationStatus.PAYMENT_SENT))
        .isEqualTo(BundleReleasability.RELEASABLE);
  }

  @Test
  void 기한_정각은_지난_것으로_본다() {
    assertThat(
            BundleReleasability.of(
                BuncheolStatus.CONFIRMED,
                bundle(NOW, null),
                List.of(slot(ParticipationStatus.AWAITING_PAYMENT)),
                NOW))
        .isEqualTo(BundleReleasability.RELEASABLE);
  }

  @ParameterizedTest
  @EnumSource(
      value = BuncheolStatus.class,
      names = "CONFIRMED",
      mode = EnumSource.Mode.EXCLUDE)
  void 진행_확정이_아니면_뺄_수_없다(final BuncheolStatus status) {
    assertThat(of(status, bundle(PAST_DUE, null), ParticipationStatus.AWAITING_PAYMENT))
        .isEqualTo(BundleReleasability.BUNCHEOL_NOT_CONFIRMED);
  }

  @Test
  void 수락_전_추가_신청은_수락이나_거절로_처리한다() {
    assertThat(of(BuncheolStatus.CONFIRMED, bundle(null, null), ParticipationStatus.APPLIED))
        .isEqualTo(BundleReleasability.NOT_ACCEPTED);
  }

  @Test
  void 기한_전이면_뺄_수_없다() {
    assertThat(
            of(BuncheolStatus.CONFIRMED, bundle(FUTURE_DUE, null), ParticipationStatus.PAYMENT_SENT))
        .isEqualTo(BundleReleasability.BEFORE_DUE);
  }

  @Test
  void 기한이_비어_있으면_닫힌다() {
    assertThat(
            of(BuncheolStatus.CONFIRMED, bundle(null, null), ParticipationStatus.AWAITING_PAYMENT))
        .isEqualTo(BundleReleasability.BEFORE_DUE);
  }

  // 확정분은 기한 안내보다 먼저 알린다.
  @Test
  void 입금_확인된_슬롯이_있으면_기한_전이어도_확정이_먼저다() {
    assertThat(
            of(
                BuncheolStatus.CONFIRMED,
                bundle(FUTURE_DUE, null),
                ParticipationStatus.CONFIRMED,
                ParticipationStatus.AWAITING_PAYMENT))
        .isEqualTo(BundleReleasability.HAS_CONFIRMED);
  }

  // 슬롯은 잠금 조회로 읽은 최신 값이고 묶음 엔티티는 낡을 수 있다 — 슬롯이 먼저다.
  @Test
  void 살아_있는_슬롯이_없으면_묶음이_낡아_열려_보여도_이미_닫힌_것이다() {
    assertThat(
            of(
                BuncheolStatus.CONFIRMED,
                bundle(PAST_DUE, null),
                ParticipationStatus.CANCELLED,
                ParticipationStatus.CANCELLED))
        .isEqualTo(BundleReleasability.ALREADY_CLOSED);
  }

  @Test
  void 묶음이_닫혔으면_이미_닫힌_것이다() {
    assertThat(
            of(
                BuncheolStatus.CONFIRMED,
                bundle(PAST_DUE, PAST_DUE),
                ParticipationStatus.AWAITING_PAYMENT))
        .isEqualTo(BundleReleasability.ALREADY_CLOSED);
  }
}
