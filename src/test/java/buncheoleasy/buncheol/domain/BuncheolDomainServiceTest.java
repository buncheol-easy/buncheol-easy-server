package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolDomainService 단위 테스트")
class BuncheolDomainServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @InjectMocks private BuncheolDomainService buncheolDomainService;

  @Mock private BuncheolRepository buncheolRepository;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Nested
  @DisplayName("호스트 분철 취소 테스트")
  class CancelBuncheolTest {

    @Test
    void 모집중_분철은_첫_CAS_로_취소되고_직전_상태_RECRUITING_을_반환한다() {
      // given
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(1);

      // when
      BuncheolStatus priorStatus = buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW);

      // then
      assertThat(priorStatus).isEqualTo(BuncheolStatus.RECRUITING);
      // 첫 CAS 가 선점했으므로 CANCELLED 대상 CAS 는 시도하지 않는다.
      then(buncheolRepository)
          .should(never())
          .finalizeIfStatus(
              anyLong(), eq(BuncheolStatus.CANCELLED), any(BuncheolStatus.class), any(Instant.class));
    }

    // 입금 수집중 취소는 범용 finalizeIfStatus 가 아니라 확정 참여 유무를 함께 보는 전용 CAS 로 간다 (docs/56 H-13).
    @Test
    void C2C_입금_수집중_분철은_확정_참여_가드_CAS_로_취소되고_직전_상태_PAYMENT_COLLECTING_을_반환한다() {
      // given
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(0);
      given(buncheolRepository.hostCancelIfCollectingAndNoConfirmed(BUNCHEOL_ID, NOW))
          .willReturn(1);

      // when
      BuncheolStatus priorStatus = buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW);

      // then
      assertThat(priorStatus).isEqualTo(BuncheolStatus.PAYMENT_COLLECTING);
      // 범용 CAS 로 입금 수집중을 전이하면 확정 참여 가드를 우회하게 된다 — 그 경로를 쓰지 않는지 못 박는다.
      then(buncheolRepository)
          .should(never())
          .finalizeIfStatus(
              anyLong(),
              eq(BuncheolStatus.PAYMENT_COLLECTING),
              any(BuncheolStatus.class),
              any(Instant.class));
    }

    @Test
    void 인원미달_자동취소_분철은_세번째_CAS_로_취소되고_직전_상태_CANCELLED_를_반환한다() {
      // given
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(0);
      given(buncheolRepository.hostCancelIfCollectingAndNoConfirmed(BUNCHEOL_ID, NOW))
          .willReturn(0);
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.CANCELLED, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(1);

      // when
      BuncheolStatus priorStatus = buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW);

      // then
      assertThat(priorStatus).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 모든_CAS_가_실패하면_취소_불가_상태로_거부한다() {
      // given — CONFIRMED·HOST_CANCELLED 등 허용 외 상태면 어떤 CAS 도 매치되지 않는다.
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(0);
      given(buncheolRepository.hostCancelIfCollectingAndNoConfirmed(BUNCHEOL_ID, NOW))
          .willReturn(0);
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.CANCELLED, BuncheolStatus.HOST_CANCELLED, NOW))
          .willReturn(0);

      // when & then
      assertThatThrownBy(() -> buncheolDomainService.cancelBuncheol(BUNCHEOL_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("최소 인원 ≤ 자리 수 검사 테스트")
  class ValidateMinHeadcountWithinSlotsTest {

    @ParameterizedTest
    @CsvSource({"3, 3", "1, 3", "1, 1"})
    void 최소_인원이_자리_수_이하면_통과한다(final int minHeadcount, final int slotCount) {
      assertThatCode(
              () -> buncheolDomainService.validateMinHeadcountWithinSlots(minHeadcount, slotCount))
          .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"4, 3", "5, 3", "2, 1"})
    void 최소_인원이_자리_수를_넘으면_BCH_123으로_거부한다(final int minHeadcount, final int slotCount) {
      assertThatThrownBy(
              () -> buncheolDomainService.validateMinHeadcountWithinSlots(minHeadcount, slotCount))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS);
    }
  }

  @Nested
  @DisplayName("자리 빼기 뒤 최소 인원 낮추기 테스트")
  class LowerMinHeadcountToSlotCountTest {

    @Test
    void 남은_자리_수로_낮추기를_저장소에_위임한다() {
      given(buncheolRepository.lowerMinHeadcountIfRecruiting(BUNCHEOL_ID, 2, NOW)).willReturn(1);

      assertThat(buncheolDomainService.lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 2, NOW)).isTrue();
    }

    // 엔티티 검증을 거치지 않는 UPDATE 라 「최소 인원 ≥ 1」이 여기서 지켜져야 한다.
    @Test
    void 남은_자리가_0이면_최소_인원_규칙으로_거부하고_쓰지_않는다() {
      assertThatThrownBy(
              () -> buncheolDomainService.lowerMinHeadcountToSlotCount(BUNCHEOL_ID, 0, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_INVALID);

      then(buncheolRepository)
          .should(never())
          .lowerMinHeadcountIfRecruiting(anyLong(), anyInt(), any());
    }
  }

  @Nested
  @DisplayName("거래 조건 수정 위임 테스트")
  class ChangeTermsTest {

    private final BuncheolTerms terms =
        new BuncheolTerms(
            Instant.parse("2026-05-20T03:00:00Z"), 2, ShippingFeePolicy.of(3000, null), "스토어");

    @Test
    void 조건부_UPDATE_가_한_행을_바꾸면_true() {
      given(buncheolRepository.changeTermsIfNoApplicant(BUNCHEOL_ID, terms, NOW)).willReturn(1);

      assertThat(buncheolDomainService.changeTermsIfNoApplicant(BUNCHEOL_ID, terms, NOW)).isTrue();
    }

    @Test
    void 조건부_UPDATE_가_0행이면_false() {
      given(buncheolRepository.changeTermsIfNoApplicant(BUNCHEOL_ID, terms, NOW)).willReturn(0);

      assertThat(buncheolDomainService.changeTermsIfNoApplicant(BUNCHEOL_ID, terms, NOW)).isFalse();
    }

    @Test
    void 최신_잠금_조회에서_분철이_없으면_404_로_거부한다() {
      given(buncheolRepository.findLatestByIdForUpdate(BUNCHEOL_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }
  }
}
