package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolAutoCloseService 단위 테스트")
class BuncheolAutoCloseServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @InjectMocks private BuncheolAutoCloseService buncheolAutoCloseService;

  @Mock private BuncheolRepository buncheolRepository;

  @Mock private BuncheolDomainService buncheolDomainService;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  @Mock private BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;

  @Mock private DeliveryDomainService deliveryDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  // 마감 판정 CAS 후 재조회한 분철의 상태로 후속 처리를 분기하므로, 상태만 stub 한 mock 으로 충분하다.
  private Buncheol buncheolWithStatus(final BuncheolStatus status) {
    Buncheol buncheol = mock(Buncheol.class);
    given(buncheol.getStatus()).willReturn(status);
    return buncheol;
  }

  @Nested
  @DisplayName("만료 분철 조회 테스트")
  class FindExpiredBuncheolIdsTest {

    @Test
    void deadline이_지난_RECRUITING_분철_id를_조회한다() {
      // C2C 는 확정 유예(48h) 컷오프를 함께 전달해 유예 중 분철을 쿼리에서 거른다.
      Instant graceCutoff = NOW.minus(48, ChronoUnit.HOURS);
      given(buncheolRepository.findRecruitingIdsPastDeadline(NOW, graceCutoff, 100))
          .willReturn(List.of(1L, 2L, 3L));

      List<Long> result = buncheolAutoCloseService.findExpiredBuncheolIds(NOW);

      assertThat(result).containsExactly(1L, 2L, 3L);
      then(buncheolRepository).should().findRecruitingIdsPastDeadline(NOW, graceCutoff, 100);
    }
  }

  @Nested
  @DisplayName("단일 분철 마감 판정 테스트")
  class FinalizeExpiredTest {

    @Test
    void CAS_가_진행확정으로_전이하면_진행확정_후속처리를_위임한다() {
      given(buncheolDomainService.finalizeExpiredByConfirmedHeadcount(BUNCHEOL_ID, NOW)).willReturn(true);
      // 중첩 stubbing(UnfinishedStubbingException) 회피를 위해 mock 을 별도 문장에서 먼저 만든다.
      Buncheol buncheol = buncheolWithStatus(BuncheolStatus.CONFIRMED);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      // 배송 스냅샷 생성·진행확정 알림은 BuncheolConfirmedFinalizer 로 위임된다(조기 확정 경로와 공유).
      then(buncheolConfirmedFinalizer).should().finalizeConfirmed(BUNCHEOL_ID);
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void CAS_가_취소로_전이하면_활성_참여를_일괄_취소하며_취소_이벤트를_발행한다() {
      given(buncheolDomainService.finalizeExpiredByConfirmedHeadcount(BUNCHEOL_ID, NOW)).willReturn(true);
      // 중첩 stubbing(UnfinishedStubbingException) 회피를 위해 mock 을 별도 문장에서 먼저 만든다.
      Buncheol buncheol = buncheolWithStatus(BuncheolStatus.CANCELLED);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      Participation cancelled = mock(Participation.class);
      given(cancelled.getId()).willReturn(701L);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
      // 개최자 취소 경로와 대칭 — 안 닫으면 마감된 분철에 활성 묶음이 영구히 남는다.
      then(participationBundleDomainService).should().closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
      // 취소된 참여의 배송 스냅샷을 정리한다(입금확인 시 생성된 고아 스냅샷 방지).
      then(deliveryDomainService).should().deleteByParticipationIds(List.of(701L));
      then(eventPublisher).should().publishEvent(any(BuncheolCancelledEvent.class));
      then(buncheolConfirmedFinalizer).should(never()).finalizeConfirmed(anyLong());
    }

    @Test
    void CAS_마감에_실패하면_후속_처리_없이_false를_반환한다() {
      // 플로우 분기용 선조회만 있고(isC2c=false 기본값), CAS 실패 후 상태 재조회·후속 처리는 없다.
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.finalizeExpiredByConfirmedHeadcount(BUNCHEOL_ID, NOW)).willReturn(false);

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isFalse();
      then(buncheolConfirmedFinalizer).should(never()).finalizeConfirmed(anyLong());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void C2C_분철은_확정_유예_안에는_마감하지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getDeadline()).willReturn(NOW.minus(1, ChronoUnit.HOURS)); // 유예(48h) 내
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isFalse();
      then(buncheolDomainService).should(never()).cancelUnconfirmedC2c(anyLong(), any());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void C2C_분철은_확정_유예가_지나면_미성사_취소하고_취소_이벤트를_발행한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getDeadline()).willReturn(NOW.minus(49, ChronoUnit.HOURS)); // 유예(48h) 경과
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.cancelUnconfirmedC2c(BUNCHEOL_ID, NOW)).willReturn(true);
      Participation cancelled = mock(Participation.class);
      given(cancelled.getId()).willReturn(702L);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
      // 개최자 취소 경로와 대칭 — 안 닫으면 마감된 분철에 활성 묶음이 영구히 남는다.
      then(participationBundleDomainService).should().closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
      then(eventPublisher).should().publishEvent(any(BuncheolCancelledEvent.class));
      then(buncheolConfirmedFinalizer).should(never()).finalizeConfirmed(anyLong());
    }
  }
}
