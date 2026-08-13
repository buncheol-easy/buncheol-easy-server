package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("개최자 분철 취소 가능 여부 판정 테스트 (docs/56 S-2)")
class BuncheolHostCancellabilityTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @Nested
  @DisplayName("판정표")
  class DecisionTest {

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "PAYMENT_COLLECTING", "CANCELLED"})
    void 취소_가능_상태에서_입금확인이_0건이면_취소할_수_있다(final BuncheolStatus status) {
      assertThat(BuncheolHostCancellability.of(status, 0L))
          .isEqualTo(BuncheolHostCancellability.CANCELLABLE);
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"CONFIRMED", "HOST_CANCELLED"})
    void 진행확정_이후와_이미_취소된_분철은_상태로_막는다(final BuncheolStatus status) {
      assertThat(BuncheolHostCancellability.of(status, 0L))
          .isEqualTo(BuncheolHostCancellability.BLOCKED_BY_STATUS);
    }

    // H-13 본체. 이 판정을 지우면 입금을 받은 뒤에도 취소가 열려 빨개진다.
    @Test
    void 입금확인이_1건이라도_있는_입금_수집중_분철은_막는다() {
      assertThat(BuncheolHostCancellability.of(BuncheolStatus.PAYMENT_COLLECTING, 1L))
          .isEqualTo(BuncheolHostCancellability.BLOCKED_BY_CONFIRMED_PAYMENT);
    }

    // 모집중·자동취소 구간에는 입금확인이 있을 수 없고, 있더라도(데이터 이상) 그 구간의 취소는 막지 않는다 —
    // 서버 CAS 도 이 두 구간엔 확정 참여 조건을 걸지 않는다.
    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING", "CANCELLED"})
    void 입금_수집중이_아니면_입금확인_수는_판정에_쓰이지_않는다(final BuncheolStatus status) {
      assertThat(BuncheolHostCancellability.of(status, 3L))
          .isEqualTo(BuncheolHostCancellability.CANCELLABLE);
    }

    @Test
    void isCancellable_은_CANCELLABLE_에서만_true() {
      assertThat(
              Arrays.stream(BuncheolHostCancellability.values())
                  .filter(BuncheolHostCancellability::isCancellable))
          .containsExactly(BuncheolHostCancellability.CANCELLABLE);
    }
  }

  /**
   * 판정의 취소 가능 상태 집합이 실제 취소 CAS 가 시도하는 상태와 어긋나면, 목록이 "취소 가능" 이라 말한 카드가 409 로 떨어진다. 전 상태를 돌며 둘이
   * 같은 답을 내는지 확인한다 — {@code CANCELLABLE_STATUSES} 나 {@code BuncheolDomainService#cancelBuncheol} 중
   * 한쪽만 바뀌면 빨개진다.
   */
  @ParameterizedTest
  @EnumSource(BuncheolStatus.class)
  @DisplayName("판정의 취소 가능 상태 집합이 취소 CAS 가 시도하는 상태와 일치한다")
  void 판정과_CAS_의_상태_집합이_일치한다(final BuncheolStatus currentStatus) {
    BuncheolRepository repository = mock(BuncheolRepository.class);
    // currentStatus 에 있는 분철을 흉내 낸다 — 그 상태를 기대하는 CAS 만 한 행을 잡는다.
    given(repository.finalizeIfStatus(anyLong(), any(), any(), any()))
        .willAnswer(
            invocation ->
                invocation.getArgument(1, BuncheolStatus.class) == currentStatus ? 1 : 0);
    given(repository.hostCancelIfCollectingAndNoConfirmed(anyLong(), any()))
        .willReturn(currentStatus == BuncheolStatus.PAYMENT_COLLECTING ? 1 : 0);
    BuncheolDomainService domainService =
        new BuncheolDomainService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(BuncheolHostCancellability.of(currentStatus, 0L).isCancellable())
        .isEqualTo(casSucceeds(domainService));
  }

  private boolean casSucceeds(final BuncheolDomainService domainService) {
    try {
      domainService.cancelBuncheol(BUNCHEOL_ID, NOW);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
