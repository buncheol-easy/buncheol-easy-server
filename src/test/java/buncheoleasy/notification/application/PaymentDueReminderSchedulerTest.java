package buncheoleasy.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import buncheoleasy.buncheol.application.PaymentDueImminentEvent;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentDueReminderScheduler 단위 테스트")
class PaymentDueReminderSchedulerTest {

  @InjectMocks private PaymentDueReminderScheduler scheduler;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("기한 임박(마감 3시간 전) 대상마다 PaymentDueImminentEvent 를 발행한다")
  void publishesForEachTarget() {
    Participation first = mock(Participation.class);
    given(first.getId()).willReturn(1L);
    Participation second = mock(Participation.class);
    given(second.getId()).willReturn(2L);
    given(
            participationDomainService.findAwaitingPaymentReminderTargets(
                Instant.parse("2026-03-11T12:00:00Z"), Instant.parse("2026-03-11T15:00:00Z")))
        .willReturn(List.of(first, second));

    scheduler.remindImminentPaymentDue();

    then(eventPublisher).should(times(2)).publishEvent(any(PaymentDueImminentEvent.class));
  }

  @Test
  @DisplayName("대상이 없으면 아무 이벤트도 발행하지 않는다")
  void publishesNothingWhenNoTarget() {
    given(participationDomainService.findAwaitingPaymentReminderTargets(any(), any()))
        .willReturn(List.of());

    scheduler.remindImminentPaymentDue();

    then(eventPublisher).shouldHaveNoInteractions();
  }
}
