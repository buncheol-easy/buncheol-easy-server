package buncheoleasy.buncheol.application;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.global.scheduler.SchedulerActivationGate;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolAutoCloseScheduler 단위 테스트")
class BuncheolAutoCloseSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

  @InjectMocks private BuncheolAutoCloseScheduler buncheolAutoCloseScheduler;

  @Mock private BuncheolAutoCloseService buncheolAutoCloseService;

  @Mock private SchedulerActivationGate schedulerActivationGate;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 만료된_분철마다_마감_판정을_시도한다() {
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(buncheolAutoCloseService.findExpiredBuncheolIds(NOW)).willReturn(List.of(1L, 2L, 3L));
    given(buncheolAutoCloseService.finalizeExpired(1L, NOW)).willReturn(true);
    given(buncheolAutoCloseService.finalizeExpired(2L, NOW)).willReturn(true);
    given(buncheolAutoCloseService.finalizeExpired(3L, NOW)).willReturn(false);

    buncheolAutoCloseScheduler.closeByFallbackPolling();

    then(buncheolAutoCloseService).should().finalizeExpired(1L, NOW);
    then(buncheolAutoCloseService).should().finalizeExpired(2L, NOW);
    then(buncheolAutoCloseService).should().finalizeExpired(3L, NOW);
  }

  @Test
  void 한_분철_마감이_예외를_던져도_나머지를_계속_처리한다() {
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(buncheolAutoCloseService.findExpiredBuncheolIds(NOW)).willReturn(List.of(1L, 2L));
    given(buncheolAutoCloseService.finalizeExpired(1L, NOW))
        .willThrow(new RuntimeException("DB 오류"));
    given(buncheolAutoCloseService.finalizeExpired(2L, NOW)).willReturn(true);

    buncheolAutoCloseScheduler.closeByFallbackPolling();

    // 1L 처리가 실패해도 2L 까지 진행돼야 한다.
    then(buncheolAutoCloseService).should().finalizeExpired(2L, NOW);
  }

  @Test
  void 만료된_분철이_없으면_마감을_시도하지_않는다() {
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(buncheolAutoCloseService.findExpiredBuncheolIds(NOW)).willReturn(List.of());

    buncheolAutoCloseScheduler.closeByFallbackPolling();

    then(buncheolAutoCloseService).should().findExpiredBuncheolIds(NOW);
    // C2C 데드엔드 정리 폴링은 마감 폴링과 독립으로 항상 1회 수행된다.
    then(buncheolAutoCloseService).should().findOverdueCollectingIds(NOW);
    then(buncheolAutoCloseService).shouldHaveNoMoreInteractions();
  }

  @Test
  void 정시_cron_도_같은_마감_로직을_탄다() {
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(buncheolAutoCloseService.findExpiredBuncheolIds(NOW)).willReturn(List.of(1L));
    given(buncheolAutoCloseService.finalizeExpired(1L, NOW)).willReturn(true);

    buncheolAutoCloseScheduler.closeAtHour();

    then(buncheolAutoCloseService).should().finalizeExpired(1L, NOW);
  }

  @Test
  void 기동_유예_중에는_아무것도_하지_않는다() {
    // 블루-그린: 트래픽 전환 전(폐기될 수 있는) 인스턴스의 마감 커밋·알림 발송 차단 (docs/39).
    // cron 은 initial-delay 보호가 없어 이 게이트가 유일한 방어 — 두 트리거 모두 검증한다.
    given(schedulerActivationGate.isActive()).willReturn(false);

    buncheolAutoCloseScheduler.closeAtHour();
    buncheolAutoCloseScheduler.closeByFallbackPolling();

    then(buncheolAutoCloseService).shouldHaveNoInteractions();
  }
}
