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
    // 커밋된 추적 동기화가 유실되지 않게 (docs/39, #92 6차 리뷰).
    // ⚠️ awaitTermination 은 "상한"이 아니다 — 타임아웃 시 Spring 은 경고만 남기고
    // shutdownNow 를 부르지 않으므로, 큐가 깊게 쌓인 상태라면 non-daemon 스레드가 JVM 종료를
    // 붙들어 stop_grace_period(45s) 만료 SIGKILL 로 끝난다. "web 30s + 5s×2 = 예산 안" 산식은
    // in-flight + 얕은 큐(평시) 한정이다 (7차 리뷰 — 진짜 상한이 필요해지면 종료 시
    // shutdownNow 래핑 또는 queueCapacity 하향을 검토).
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }
}
