package buncheoleasy.push.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 앱 푸시 전용 스레드풀. 애플 호출은 기기당 최대 연결 3s + 응답 5s 를 잡으므로, 기본 {@code applicationTaskExecutor}(이미지 업로드·페이액션
 * 주문·슬랙)와 같이 쓰면 애플이 느릴 때 그 작업들이 밀린다 — 배송 추적({@code TrackingExecutorConfig})과 같은 이유로 떼어 낸다.
 *
 * <p>푸시는 수신함·알림톡의 덤이라 잃어도 되는 쪽으로 정했다:
 *
 * <ul>
 *   <li>큐가 넘치면 WARN 로그만 남기고 버린다. ⚠️ CallerRuns 금지 — 제출 스레드가 알림톡 단일 스레드라, 거기서 애플을 부르면 알림톡 줄이 막힌다.
 *   <li>종료 때 기다리지 않는다(진행 중 작업은 인터럽트, 큐는 폐기). 그래서 종료 예산(application.yaml {@code server.shutdown} 주석)에
 *       더하지 않는다.
 * </ul>
 */
@Slf4j
@Configuration
public class PushExecutorConfig {

  public static final String PUSH_EXECUTOR = "pushExecutor";

  static final int POOL_SIZE = 2;
  static final int QUEUE_CAPACITY = 500;

  @Bean(PUSH_EXECUTOR)
  public ThreadPoolTaskExecutor pushExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("push-");
    executor.setCorePoolSize(POOL_SIZE);
    executor.setMaxPoolSize(POOL_SIZE);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setRejectedExecutionHandler(
        (task, pool) ->
            log.warn("앱 푸시 대기열이 가득 차 이번 알림의 푸시를 버림 - queueCapacity={}", QUEUE_CAPACITY));
    executor.setWaitForTasksToCompleteOnShutdown(false);
    return executor;
  }
}
