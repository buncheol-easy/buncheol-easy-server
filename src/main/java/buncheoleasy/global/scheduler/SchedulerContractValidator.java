package buncheoleasy.global.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 스케줄러 타이밍 계약을 기동 시점에 강제한다 — "장주기 fixedDelay 의 initial-delay 는 기동
 * 유예({@link SchedulerActivationGate})보다 커야 한다". 위반은 조용한 장애다: 첫 발화가 유예에
 * skip 되고 다음 기회는 주기(추적 갱신 12h) 뒤라, 배포 주기가 그보다 짧으면 스케줄러가 영구히
 * 돌지 않는다(웹훅 TTL 갱신 중단 → 48h 후 추적 콜백 단절 — #92 6차 리뷰가 잡은 실장애 시나리오).
 *
 * <p>주석 3곳에만 있던 계약을 코드로 내린 것(7차 리뷰) — 두 값은 독립 env 로 열려 있어 운영자가
 * 유예만 올리면(스크립트 경고의 안내 경로) 계약이 조용히 깨졌다. fail-fast 로 부팅을 막으면
 * 블루-그린 헬스 게이트에서 배포가 실패할 뿐 서빙은 무영향이고, 원인이 즉시 드러난다.
 */
@Component
public class SchedulerContractValidator {

  public SchedulerContractValidator(
      final SchedulerActivationGate schedulerActivationGate,
      // 인라인 기본값 없음 — 키가 사라지는 것도 계약 위반으로 시끄럽게 죽어야 한다.
      @Value("${app.delivery.tracking-refresh.initial-delay-ms}") final long trackingInitialDelayMs) {
    long graceMillis = schedulerActivationGate.graceMillis();
    if (trackingInitialDelayMs <= graceMillis) {
      throw new IllegalStateException(
          "스케줄러 계약 위반: tracking-refresh initial-delay(%dms)가 기동 유예(%dms) 이하다 — 첫 발화가 유예에 skip 되고 12h 주기라 배포 주기에 따라 영구 미실행이 된다. TRACKING_REFRESH_INITIAL_DELAY_MS 를 유예보다 크게 잡아라 (docs/39)"
              .formatted(trackingInitialDelayMs, graceMillis));
    }
  }
}
