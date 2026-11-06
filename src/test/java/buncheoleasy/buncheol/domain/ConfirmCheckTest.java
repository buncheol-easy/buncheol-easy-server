package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ConfirmCheck — 회원 개최 진행 확정 판정")
class ConfirmCheckTest {

  private static final Instant DUE = Instant.parse("2026-11-06T03:00:00Z");

  private static ConfirmCheck of(final long confirmed, final long unconfirmed, final Instant now) {
    return ConfirmCheck.of(BuncheolStatus.PAYMENT_COLLECTING, confirmed, unconfirmed, DUE, now);
  }

  private static ConfirmCheck ofActive(final Instant now, final ParticipationStatus... statuses) {
    List<Participation> active =
        Arrays.stream(statuses)
            .map(
                status -> {
                  Participation participation = mock(Participation.class);
                  given(participation.getStatus()).willReturn(status);
                  return participation;
                })
            .toList();
    return ConfirmCheck.of(BuncheolStatus.PAYMENT_COLLECTING, active, DUE, now);
  }

  @Test
  void 모두_입금_확인됐으면_기한_전이라도_ALL_PAID() {
    assertThat(of(3, 0, DUE.minusSeconds(3600))).isEqualTo(ConfirmCheck.ALL_PAID);
  }

  @Test
  void 미확인이_있고_기한_전이면_BEFORE_DUE() {
    assertThat(of(2, 1, DUE.minusSeconds(1))).isEqualTo(ConfirmCheck.BEFORE_DUE);
  }

  @Test
  void 미확인이_있어도_기한_정각부터는_DUE_PASSED() {
    assertThat(of(2, 1, DUE)).isEqualTo(ConfirmCheck.DUE_PASSED);
    assertThat(of(2, 1, DUE.plusSeconds(1))).isEqualTo(ConfirmCheck.DUE_PASSED);
  }

  // 확인된 자리가 0 이면 기한이 지나도 진행 확정할 것이 없다 — 배지도 띄우지 않는다.
  @Test
  void 확인된_참여가_없으면_기한과_무관하게_NO_CONFIRMED() {
    assertThat(of(0, 2, DUE.minusSeconds(1))).isEqualTo(ConfirmCheck.NO_CONFIRMED);
    assertThat(of(0, 2, DUE.plusSeconds(1))).isEqualTo(ConfirmCheck.NO_CONFIRMED);
    assertThat(of(0, 0, DUE.plusSeconds(1))).isEqualTo(ConfirmCheck.NO_CONFIRMED);
  }

  @ParameterizedTest
  @EnumSource(
      value = BuncheolStatus.class,
      names = {"RECRUITING", "CONFIRMED", "CANCELLED", "HOST_CANCELLED"})
  void 입금_수집_중이_아니면_null(final BuncheolStatus status) {
    assertThat(ConfirmCheck.of(status, 2, 0, DUE, DUE)).isNull();
  }

  // 입금 수집 중인데 기한이 비었으면 데이터 이상이다 — 미확인 참여를 빼는 쪽으로 열지 않는다.
  @Test
  void 기한이_비었으면_지나지_않은_것으로_본다() {
    assertThat(ConfirmCheck.of(BuncheolStatus.PAYMENT_COLLECTING, 1, 1, null, DUE))
        .isEqualTo(ConfirmCheck.BEFORE_DUE);
  }

  // 참여 목록을 받는 판정은 확인 수와 미확인(입금 대기·보냈어요) 수를 세어 같은 판정을 탄다.
  @Test
  void 참여_목록에서_확인과_입금_대기_보냈어요를_센다() {
    assertThat(
            ofActive(
                DUE.minusSeconds(1), ParticipationStatus.CONFIRMED, ParticipationStatus.CONFIRMED))
        .isEqualTo(ConfirmCheck.ALL_PAID);
    assertThat(
            ofActive(
                DUE.minusSeconds(1),
                ParticipationStatus.CONFIRMED,
                ParticipationStatus.AWAITING_PAYMENT))
        .isEqualTo(ConfirmCheck.BEFORE_DUE);
    assertThat(ofActive(DUE, ParticipationStatus.CONFIRMED, ParticipationStatus.PAYMENT_SENT))
        .isEqualTo(ConfirmCheck.DUE_PASSED);
    assertThat(ofActive(DUE, ParticipationStatus.PAYMENT_SENT))
        .isEqualTo(ConfirmCheck.NO_CONFIRMED);
    assertThat(ConfirmCheck.of(BuncheolStatus.CONFIRMED, List.of(), DUE, DUE)).isNull();
  }

  // 신청(APPLIED)은 입금 확인 대상이 아니라 미확인으로 세지 않는다 — 진행 확정이 빼는 대상(입금 대기·보냈어요)과 같다.
  @Test
  void 참여_목록의_신청은_미확인으로_세지_않는다() {
    assertThat(
            ofActive(
                DUE.minusSeconds(1), ParticipationStatus.CONFIRMED, ParticipationStatus.APPLIED))
        .isEqualTo(ConfirmCheck.ALL_PAID);
  }

  @Test
  void 진행_확정_버튼은_ALL_PAID_와_DUE_PASSED_에서만_켜진다() {
    assertThat(ConfirmCheck.ALL_PAID.isConfirmable()).isTrue();
    assertThat(ConfirmCheck.DUE_PASSED.isConfirmable()).isTrue();
    assertThat(ConfirmCheck.BEFORE_DUE.isConfirmable()).isFalse();
    assertThat(ConfirmCheck.NO_CONFIRMED.isConfirmable()).isFalse();
  }
}
