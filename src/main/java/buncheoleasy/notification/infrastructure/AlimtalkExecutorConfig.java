package buncheoleasy.notification.infrastructure;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림톡 발송 전용 스레드풀. <b>스레드가 하나인 것이 이 빈의 핵심</b>이다 — 풀 크기는 성능 조정값이 아니라 발송 순서를 지키기 위한 제약이다.
 *
 * <p>발송 핸들러는 모두 {@code @Async} + {@code @TransactionalEventListener(AFTER_COMMIT)} 인데, 한 트랜잭션이 이벤트를 둘
 * 이상 발행하면 그 핸들러들이 커밋 후 <b>동시에</b> 제출된다. 이 빈이 생기기 전에는 executor 를 지정하지 않아
 * {@code SimpleAsyncTaskExecutor}(호출마다 새 스레드) 로 떨어졌고 — {@code applicationTaskExecutor} 는 다른 전용 풀 때문에
 * 애초에 등록되지 않았다, {@code spring.task.execution.mode} 참고 — 태스크마다 스레드가 따로라 순서가 실행마다 갈렸다. 실제로 마지막 참여자의 입금을 확인하는 순간 {@code PaymentConfirmedEvent} 와 {@code
 * BuncheolConfirmedEvent} 가 같은 트랜잭션에서 발행돼(전원 입금확인 시 조기 진행확정), 참여자에게 <b>"진행이 확정됐어요" 가 "입금을
 * 확인했어요" 보다 먼저 도착</b>하는 일이 있었다.
 *
 * <p>스레드가 하나면 제출 순서 = 실행 순서다. Spring 은 커밋 후 리스너를 <b>이벤트 발행 순서대로</b> 호출하므로, 단일 스레드 FIFO 큐에 얹는 것만으로
 * 도메인에서 일어난 순서가 사용자 휴대폰에 도착하는 순서로 이어진다.
 *
 * <p><b>직렬화 비용은 한계가 있다</b> — 알리고 호출은 connect 3s + read 5s 로 묶여 있어(application.yaml {@code aligo}) 한 건이
 * 큐를 무한정 붙들 수 없다. 지금 규모에서 한 이벤트의 수신자는 많아야 분철 정원 수준이라 직렬 발송으로 충분하다. 처리량이 문제가 되면 스레드를 늘릴 것이 아니라
 * <b>같은 수신자·같은 분철끼리만 순서를 보장하는 파티셔닝</b>으로 가야 한다 — 스레드를 늘리는 순간 이 빈이 막으려던 문제가 그대로 돌아온다.
 */
@Configuration
public class AlimtalkExecutorConfig {

  public static final String ALIMTALK_EXECUTOR = "alimtalkExecutor";

  @Bean(ALIMTALK_EXECUTOR)
  public ThreadPoolTaskExecutor alimtalkExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("alimtalk-");
    // ⚠️ 1 을 바꾸면 순서 보장이 사라진다 (위 설명 참고).
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    // 큐가 가득 차면 제출 스레드가 직접 실행(CallerRuns). 이때만 순서가 어긋날 수 있는데, 알림을 버리는 것보다는 낫다고 봤다 —
    // 1000 건이 밀려 있어야 도달하는 경로이고, 그 상황에서 지켜야 할 것은 순서가 아니라 발송 자체다.
    executor.setQueueCapacity(1000);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // 블루-그린 드레인 중 커밋된 알림이 유실되지 않도록 잠시 기다린다 (docs/39 · TrackingExecutorConfig 와 같은 정책).
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }
}
