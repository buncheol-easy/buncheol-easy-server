package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BundleReleasability — 개최자가 언제 묶음을 「제외」할 수 있나")
class BundleReleasabilityTest {

  private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
  private static final Instant PAST = NOW.minusSeconds(3600);
  private static final Instant FUTURE = NOW.plusSeconds(3600);

  private static ParticipationBundle bundle(final Instant dueAt, final Instant closedAt) {
    ParticipationBundle bundle =
        ParticipationBundle.open(
            104L, 10L, 1L, 3_000L, new RefundAccount("국민", "1234", "홍길동"), dueAt);
    setField(bundle, "id", 141L);
    setField(bundle, "closedAt", closedAt);
    return bundle;
  }

  private static Participation slot(final long id, final ParticipationStatus status) {
    Participation participation =
        Participation.createApplied(104L, 500L + id, 10L, 1L, 10_000L, 0L);
    setField(participation, "id", id);
    setField(participation, "bundleId", 141L);
    setField(participation, "status", status);
    return participation;
  }

  @Test
  @DisplayName("입금 기한이 지났고 확정 슬롯이 없으면 제외할 수 있다")
  void releasableAfterDue() {
    assertThat(
            BundleReleasability.of(
                bundle(PAST, null), List.of(slot(1L, ParticipationStatus.AWAITING_PAYMENT)), NOW))
        .isEqualTo(BundleReleasability.RELEASABLE);
  }

  // 🔴 참여자 입장에서 아직 확정도 안 됐는데 갑자기 빠지면 자기가 뭘 잘못했나 싶어진다.
  // 「제외」는 미입금자를 정리하는 도구지 사람을 고르는 도구가 아니다 (2026-08-31 사용자 결정).
  @Test
  @DisplayName("모집 중(기한 없음)에는 제외할 수 없다")
  void notReleasableWhileRecruiting() {
    assertThat(
            BundleReleasability.of(
                bundle(null, null), List.of(slot(1L, ParticipationStatus.APPLIED)), NOW))
        .isEqualTo(BundleReleasability.RECRUITING);
  }

  // 이체가 늦게 찍혀 정상 입금자를 빼는 사고가 나면 복구 경로가 문의뿐이다.
  @Test
  @DisplayName("입금 기한 전에는 「보냈어요」여도 제외할 수 없다")
  void notReleasableBeforeDue() {
    assertThat(
            BundleReleasability.of(
                bundle(FUTURE, null), List.of(slot(1L, ParticipationStatus.PAYMENT_SENT)), NOW))
        .isEqualTo(BundleReleasability.BEFORE_DUE);
  }

  @Test
  @DisplayName("입금확인된 슬롯이 하나라도 있으면 기한이 지나도 제외할 수 없다")
  void notReleasableWithConfirmedSlot() {
    assertThat(
            BundleReleasability.of(
                bundle(PAST, null),
                List.of(
                    slot(1L, ParticipationStatus.AWAITING_PAYMENT),
                    slot(2L, ParticipationStatus.CONFIRMED)),
                NOW))
        .isEqualTo(BundleReleasability.HAS_CONFIRMED);
  }

  // 검사 순서가 계약이다 — 확정 슬롯이 있으면서 기한도 안 된 묶음은 "기한을 기다리세요" 가 아니라
  // "확정분은 뺄 수 없어요" 라고 답해야 한다.
  @Test
  @DisplayName("확정 슬롯과 기한 미도래가 겹치면 확정 사유가 이긴다")
  void confirmedWinsOverBeforeDue() {
    assertThat(
            BundleReleasability.of(
                bundle(FUTURE, null), List.of(slot(1L, ParticipationStatus.CONFIRMED)), NOW))
        .isEqualTo(BundleReleasability.HAS_CONFIRMED);
  }

  @Test
  @DisplayName("이미 끝난 묶음은 제외 대상이 아니다")
  void closedBundleIsNotReleasable() {
    assertThat(
            BundleReleasability.of(
                bundle(PAST, PAST), List.of(slot(1L, ParticipationStatus.CANCELLED)), NOW))
        .isEqualTo(BundleReleasability.ALREADY_CLOSED);
  }

  // 🟢 기한이 없으면 거부이므로 fail-closed 다 — 배포선 창에서 기한이 안 채워진 묶음이 있어도 안전하다.
  @Test
  @DisplayName("기한이 비어 있으면 안전한 쪽(거부)으로 닫힌다")
  void failsClosedWhenDueAtMissing() {
    assertThat(
            BundleReleasability.of(
                bundle(null, null),
                List.of(slot(1L, ParticipationStatus.AWAITING_PAYMENT)),
                NOW))
        .isNotEqualTo(BundleReleasability.RELEASABLE);
  }
}
