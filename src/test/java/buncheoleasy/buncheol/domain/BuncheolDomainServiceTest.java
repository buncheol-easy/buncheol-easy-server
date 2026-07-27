package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void 인원미달_자동취소_분철은_두번째_CAS_로_취소되고_직전_상태_CANCELLED_를_반환한다() {
      // given
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
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
    void 두_CAS_모두_실패하면_취소_불가_상태로_거부한다() {
      // given — CONFIRMED·HOST_CANCELLED 등 허용 외 상태면 어떤 CAS 도 매치되지 않는다.
      given(
              buncheolRepository.finalizeIfStatus(
                  BUNCHEOL_ID, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, NOW))
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
}
