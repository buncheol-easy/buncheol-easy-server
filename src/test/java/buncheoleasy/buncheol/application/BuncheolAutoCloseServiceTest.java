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
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Duration;
import java.time.Instant;
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

  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;

  @Mock private ApplicationEventPublisher eventPublisher;

  // 실제 Buncheol 을 쓴다. mock + given 으로 만들면 given(getBuncheol(...)).willReturn(...) 의 인자 평가 중
  // 중첩 stubbing 이 되어 UnfinishedStubbingException 이 발생하므로 진짜 객체로 minHeadcount 만 채운다.
  private Buncheol buncheolWithMinHeadcount(final int minHeadcount) {
    return Buncheol.create(
        1L,
        new BuncheolParams(
            1L, "제목", null, "스토어", NOW.plus(Duration.ofDays(1)), minHeadcount, 3000, null),
        NOW);
  }

  @Nested
  @DisplayName("만료 분철 조회 테스트")
  class FindExpiredBuncheolIdsTest {

    @Test
    void deadline이_지난_RECRUITING_분철_id를_조회한다() {
      given(buncheolRepository.findRecruitingIdsPastDeadline(NOW, 100))
          .willReturn(List.of(1L, 2L, 3L));

      List<Long> result = buncheolAutoCloseService.findExpiredBuncheolIds(NOW);

      assertThat(result).containsExactly(1L, 2L, 3L);
      then(buncheolRepository).should().findRecruitingIdsPastDeadline(NOW, 100);
    }
  }

  @Nested
  @DisplayName("단일 분철 마감 판정 테스트")
  class FinalizeExpiredTest {

    @Test
    void 입금확인_인원이_최소_인원_이상이면_진행확정하고_배송_스냅샷과_확정_이벤트를_발행한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheolWithMinHeadcount(2));
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(3);
      given(buncheolDomainService.finalizeBuncheol(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW))
          .willReturn(true);
      Participation confirmed = mock(Participation.class);
      given(confirmed.getId()).willReturn(601L);
      given(participationDomainService.findConfirmedByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(confirmed));

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(deliverySnapshotCreator).should().create(confirmed);
      then(eventPublisher).should().publishEvent(any(BuncheolConfirmedEvent.class));
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void 입금확인_인원이_최소_인원과_정확히_같으면_진행확정한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheolWithMinHeadcount(2));
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(2);
      given(buncheolDomainService.finalizeBuncheol(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW))
          .willReturn(true);
      given(participationDomainService.findConfirmedByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    }

    @Test
    void 입금확인_인원이_최소_인원_미만이면_취소하고_활성_참여를_일괄_취소하며_취소_이벤트를_발행한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheolWithMinHeadcount(3));
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(1);
      given(buncheolDomainService.finalizeBuncheol(BUNCHEOL_ID, BuncheolStatus.CANCELLED, NOW))
          .willReturn(true);
      Participation cancelled = mock(Participation.class);
      given(cancelled.getId()).willReturn(701L);
      given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
      then(eventPublisher).should().publishEvent(any(BuncheolCancelledEvent.class));
      then(deliverySnapshotCreator).should(never()).create(any());
    }

    @Test
    void CAS_마감에_실패하면_후속_처리_없이_false를_반환한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheolWithMinHeadcount(2));
      given(participationDomainService.countConfirmedByBuncheolId(BUNCHEOL_ID)).willReturn(3);
      given(buncheolDomainService.finalizeBuncheol(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW))
          .willReturn(false);

      boolean result = buncheolAutoCloseService.finalizeExpired(BUNCHEOL_ID, NOW);

      assertThat(result).isFalse();
      then(participationDomainService).should(never()).findConfirmedByBuncheolId(anyLong());
      then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
      then(deliverySnapshotCreator).should(never()).create(any());
      then(eventPublisher).should(never()).publishEvent(any());
    }
  }
}
