package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolConfirmedFinalizer 단위 테스트")
class BuncheolConfirmedFinalizerTest {

  @InjectMocks private BuncheolConfirmedFinalizer finalizer;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private static final Long BUNCHEOL_ID = 7L;

  @Nested
  @DisplayName("진행확정 알림 발행(finalizeConfirmed)")
  class FinalizeConfirmed {

    @Test
    @DisplayName("입금확인된 참여 전체를 한 이벤트에 실어 1건만 발행한다")
    void publishesSingleEventWithAllConfirmedIds() {
      List<Participation> confirmed =
          List.of(participation(11L), participation(12L), participation(13L));
      given(participationDomainService.findConfirmedByBuncheolId(BUNCHEOL_ID))
          .willReturn(confirmed);

      finalizer.finalizeConfirmed(BUNCHEOL_ID);

      assertThat(captureEvent().participationIds()).containsExactly(11L, 12L, 13L);
    }

    @Test
    @DisplayName("발행 이벤트에 분철 id 를 함께 싣는다")
    void carriesBuncheolId() {
      List<Participation> confirmed = List.of(participation(11L));
      given(participationDomainService.findConfirmedByBuncheolId(BUNCHEOL_ID))
          .willReturn(confirmed);

      finalizer.finalizeConfirmed(BUNCHEOL_ID);

      assertThat(captureEvent().buncheolId()).isEqualTo(BUNCHEOL_ID);
    }

    @Test
    @DisplayName("입금확인된 참여가 없으면 빈 목록으로 발행해 수신자가 생기지 않는다")
    void publishesEmptyListWhenNoConfirmed() {
      given(participationDomainService.findConfirmedByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());

      finalizer.finalizeConfirmed(BUNCHEOL_ID);

      assertThat(captureEvent().participationIds()).isEmpty();
    }

    private Participation participation(final Long id) {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(id);
      return participation;
    }

    private BuncheolConfirmedEvent captureEvent() {
      ArgumentCaptor<BuncheolConfirmedEvent> captor =
          ArgumentCaptor.forClass(BuncheolConfirmedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      return captor.getValue();
    }
  }
}
