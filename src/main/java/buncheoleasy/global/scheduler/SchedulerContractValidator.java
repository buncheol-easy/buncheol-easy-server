package buncheoleasy.global.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 스케줄러 타이밍 계약을 기동 시점에 강제한다 — "장주기 fixedDelay 의 initial-delay 는 기동
 * 유예({@link SchedulerActivationGate}) + 마진보다 커야 한다". 위반은 조용한 장애다: 첫 발화가
 * 유예에 skip 되고 다음 기회는 주기(추적 갱신 12h) 뒤라, 배포 주기가 그보다 짧으면 스케줄러가
 * 영구히 돌지 않는다(웹훅 TTL 갱신 중단 → 48h 후 추적 콜백 단절 — #92 6차 리뷰의 실장애 시나리오).
 *
 * <p>마진이 필요한 이유(8차 리뷰): 두 시계의 원점이 다르다 — initial-delay 는 컨텍스트 refresh
 * 시점부터, 유예는 {@code ApplicationReadyEvent} 부터 돈다. 그 사이에 ApplicationRunner 들이
 * 실행되므로 실제 조건은 {@code initialDelay > grace + (ready - refresh)} 다. 마진 없이 경계만
 * 검증하면 "통과했는데 여유 1초" 같은 함정이 남는다.
 *
 * <p>스케줄러가 꺼져 있으면({@code TRACKING_REFRESH_ENABLED=false}) 검증도 건너뛴다 — 외부 API
 * 장애로 스케줄러만 급히 끄는 경로를 delay 값이 막아서는 안 된다(8차 리뷰).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.delivery.tracking-refresh",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulerContractValidator {

  // refresh → ready(러너 실행) 구간 흡수용 최소 마진. 러너가 이보다 오래 걸리면 어차피
  // 헬스 게이트(180s)가 먼저 배포를 실패시킨다.
  static final long MIN_MARGIN_MS = 60_000L;

  public SchedulerContractValidator(
      final SchedulerActivationGate schedulerActivationGate,
      // 인라인 기본값 없음 — 키가 사라지는 것도 계약 위반으로 시끄럽게 죽어야 한다.
      @Value("${app.delivery.tracking-refresh.initial-delay-ms}") final long trackingInitialDelayMs) {
    long graceMillis = schedulerActivationGate.graceMillis();
    if (trackingInitialDelayMs <= graceMillis + MIN_MARGIN_MS) {
      throw new IllegalStateException(
          "스케줄러 계약 위반: tracking-refresh initial-delay(%dms)가 기동 유예(%dms)+마진(%dms) 이하다 — 첫 발화가 유예에 skip 되고 12h 주기라 배포 주기에 따라 영구 미실행이 된다. TRACKING_REFRESH_INITIAL_DELAY_MS 를 더 크게 잡아라 (docs/39)"
              .formatted(trackingInitialDelayMs, graceMillis, MIN_MARGIN_MS));
    }
  }
}
