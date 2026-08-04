package buncheoleasy.delivery.infrastructure;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 배송 추적 비동기 작업 전용 스레드풀. 추적 동기화·웹훅 등록은 외부 HTTP(read-timeout 15s)에 묶이므로, 기본
 * {@code applicationTaskExecutor} 를 알림톡 발송과 공유하면 콜백 폭주 시 알림까지 밀린다 — 풀을 분리해 서로 굶기지 않는다.
 */
@Configuration
public class TrackingExecutorConfig {

  public static final String TRACKING_EXECUTOR = "trackingExecutor";

  @Bean(TRACKING_EXECUTOR)
  public ThreadPoolTaskExecutor trackingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("tracking-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    // 큐가 가득 차면 제출 스레드가 직접 실행(CallerRuns) — 작업을 버리지 않되 무한 큐 성장을 막는다.
    executor.setQueueCapacity(1000);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // 종료 시 큐를 폐기(shutdownNow)하지 않고 잠시 기다린다 — 블루-그린 드레인 막바지에
    // 커밋된 추적 동기화가 유실되지 않게. 대기값은 compose stop_grace_period(45s) 예산 안
    // (web graceful 30s 이후 빈 소멸 단계에서 소진 — docs/39, #92 6차 리뷰).
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }
}
