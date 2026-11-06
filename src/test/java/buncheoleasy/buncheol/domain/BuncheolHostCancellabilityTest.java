package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
        names = {"RECRUITING", "CANCELLED"})
    void 모집중과_인원미달_자동취소는_취소할_수_있다(final BuncheolStatus status) {
      assertThat(BuncheolHostCancellability.of(status))
          .isEqualTo(BuncheolHostCancellability.CANCELLABLE);
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"CONFIRMED", "HOST_CANCELLED"})
    void 진행확정_이후와_이미_취소된_분철은_상태로_막는다(final BuncheolStatus status) {
      assertThat(BuncheolHostCancellability.of(status))
          .isEqualTo(BuncheolHostCancellability.BLOCKED_BY_STATUS);
    }

    // 계좌가 공개된 뒤에는 입금 확인 건수와 무관하게 운영진과 함께 접는다.
    @Test
    void 입금_수집중은_운영진_경유다() {
      assertThat(BuncheolHostCancellability.of(BuncheolStatus.PAYMENT_COLLECTING))
          .isEqualTo(BuncheolHostCancellability.NEEDS_STAFF);
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
   * 같은 답을 내는지 확인한다 — 판정이나 {@code BuncheolDomainService#cancelBuncheol} 중 한쪽만 바뀌면 빨개진다.
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
    BuncheolDomainService domainService =
        new BuncheolDomainService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(BuncheolHostCancellability.of(currentStatus).isCancellable())
        .isEqualTo(casSucceeds(domainService));
  }

  // catch 를 BusinessException + 에러코드까지 좁힌다 — RuntimeException 을 통째로 삼키면 스텁 실수나 NPE 도
  // "CAS 실패" 로 흡수돼, 취소 불가 상태(기대값 false)에서는 테스트가 깨져도 초록으로 남는다.
  private boolean casSucceeds(final BuncheolDomainService domainService) {
    try {
      domainService.cancelBuncheol(BUNCHEOL_ID, NOW);
      return true;
    } catch (BusinessException e) {
      assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
      return false;
    }
  }
}
