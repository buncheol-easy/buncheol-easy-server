package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolCancelledFinalizer 단위 테스트")
class BuncheolCancelledFinalizerTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private BuncheolCancelledFinalizer finalizer() {
    return new BuncheolCancelledFinalizer(
        participationDomainService,
        participationBundleDomainService,
        deliveryDomainService,
        eventPublisher);
  }

  // 운영진 파기 로그가 이 수를 남긴다 — 스냅샷이 아니라 실제로 전이돼 다시 읽힌 참여 수다.
  @Test
  void 실제로_취소된_참여_수를_돌려준다() {
    Participation first = mock(Participation.class);
    given(first.getId()).willReturn(601L);
    Participation second = mock(Participation.class);
    given(second.getId()).willReturn(602L);
    given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
        .willReturn(List.of(first, second));

    int cancelled =
        finalizer().finalizeCancelled(BUNCHEOL_ID, NOW, BuncheolCancelReason.HOST_CANCELLED);

    assertThat(cancelled).isEqualTo(2);
  }

  @Test
  void 취소된_참여가_없으면_0_이고_취소_안내를_보내지_않는다() {
    given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
        .willReturn(List.of());

    int cancelled =
        finalizer().finalizeCancelled(BUNCHEOL_ID, NOW, BuncheolCancelReason.HOST_CANCELLED);

    assertThat(cancelled).isZero();
    then(eventPublisher).should(never()).publishEvent(any());
  }
}
