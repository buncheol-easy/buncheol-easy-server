package buncheoleasy.global.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 블루-그린 배포의 기동 유예 게이트. 새 인스턴스는 트래픽 전환 "전"(아직 폐기될 수 있는 상태)부터
 * 스케줄러 시계가 돌기 시작하므로, 준비 완료 후 유예가 지나기 전에는 스케줄 실행을 막아 미검증
 * 빌드가 운영 DB 전이·알림 발송을 저지르는 것을 차단한다 — 스위치 복귀(롤백)는 코드만 되돌리지
 * 이 부작용은 되돌리지 못한다 (루트 docs/39, server#92 5차 리뷰).
 *
 * <p>initial-delay 로는 불충분하다 — <b>cron 트리거는 initial-delay 의 보호를 받지 않는다</b>
 * (정각 직전에 기동한 인스턴스는 기동 수십 초 만에 cron 이 발화한다). 그래서 시간 지연이 아니라
 * 모든 스케줄러 진입부가 공유하는 명시적 게이트로 강제한다.
 *
 * <p>유예 기본 300s 는 전환 스크립트(scripts/blue-green.sh)의 최악 경로 — 헬스 게이트 180s +
 * 웜업 15s + 드레인 30s + 생존 프로브 9s ≈ 234s — 에 여유를 더해 유도했다. 스크립트의 예산
 * (HEALTH_DEADLINE·DRAIN_SECONDS)을 늘리면 이 값도 같이 검토해야 한다. 트레이드오프는 재기동
 * 후 스케줄 재개가 유예만큼 늦는 것뿐이다(정각 마감은 fallback 폴링이, 입금 만료는 30분 단위
 * 기한이 흡수 — 각 스케줄러 주석 참조).
 */
@Slf4j
@Component
public class SchedulerActivationGate {

  private final Clock clock;
  private final Duration grace;

  // ApplicationReadyEvent 스레드가 쓰고 스케줄러 스레드들이 읽는다 — volatile 로 가시성 보장.
  private volatile Instant readyAt;

  public SchedulerActivationGate(
      final Clock clock,
      @Value("${app.scheduler.activation-grace:300s}") final Duration grace) {
    this.clock = clock;
    // 인라인 기본값(:300s)은 키 부재만 커버한다 — 빈 env 값("")은 null 로 변환되므로 명시 폴백까지 둔다.
    this.grace = grace == null ? Duration.ofSeconds(300) : grace;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void markReady() {
    readyAt = Instant.now(clock);
    log.info("스케줄러 기동 유예 시작 — {} 후 활성화 (블루-그린 전환 전 부작용 차단)", grace);
  }

  /** 기동 유예가 끝나 스케줄 실행이 허용되는지. 스케줄러는 false 면 로그만 남기고 건너뛴다. */
  public boolean isActive() {
    Instant at = readyAt;
    return at != null && Duration.between(at, Instant.now(clock)).compareTo(grace) >= 0;
  }
}
