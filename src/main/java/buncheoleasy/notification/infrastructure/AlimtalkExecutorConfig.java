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
 * <p><b>직렬화 비용 — 실질 부하는 한 이벤트의 수신자 수가 아니라 스케줄러 배치다.</b> 알리고 호출은 connect 3s + read 5s 로 묶여 있어
 * (application.yaml {@code aligo}) 한 건이 큐를 무한정 붙들지는 않지만, 큐에 들어오는 것은 한 이벤트가 아니다. {@code
 * ParticipationPaymentExpiryService}(BATCH_SIZE 200, 건별 트랜잭션)와 {@code BuncheolAutoCloseService}(BATCH_SIZE
 * 100 × 확정 참여자 수)가 한 틱에 독립 이벤트를 대량 발행한다. 만료 200건이면 정상 응답(300ms)에도 <b>약 60초</b>, 최악(8s)이면
 * 27분 동안 큐가 점유되고 그 뒤에 붙은 알림이 그만큼 밀린다.
 *
 * <p><b>임계값</b>: 한 틱의 발송 대상이 <b>50건</b>을 넘기 시작하면(= 최악 지연이 분 단위로 올라가면) 스레드를 늘릴 것이 아니라 <b>같은
 * 수신자·같은 분철끼리만 순서를 보장하는 파티셔닝</b>으로 넘어간다 — 스레드를 늘리는 순간 이 빈이 막으려던 문제가 그대로 돌아온다. 지금 prod 의 활성
 * 분철은 한 자릿수라 배치가 그 규모에 닿지 않는다.
 *
 * <p><b>전제 — 어느 핸들러에도 {@code @Order} 를 붙이면 안 된다.</b> 커밋 후 동기화는 {@code OrderComparator} 로 먼저 정렬되고
 * 발행 순서는 <b>동순위 안에서만</b> 유지된다. {@code @Order} 가 하나라도 붙으면 발행 순서보다 order 가 이겨 이 빈의 보장이 무너진다
 * (가드: {@code AlimtalkExecutorConfigTest}).
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
    // 블루-그린 드레인 중 커밋된 알림이 유실되지 않도록 잠시 기다린다 (docs/39).
    // ⚠️ awaitTermination 은 "상한" 이 아니다 — 타임아웃 시 Spring 은 경고만 남기고 shutdownNow 를 부르지 않으므로,
    //    큐가 깊게 쌓인 상태라면 non-daemon 스레드가 JVM 종료를 붙들어 stop_grace_period 만료 SIGKILL 로 끝난다.
    //    이 풀은 소비 스레드가 1개라 같은 큐 깊이를 tracking(4스레드)의 4배 시간에 비운다 — 그쪽보다 구조적으로
    //    도달하기 쉬우니, 큐가 실제로 깊어지기 시작하면 파티셔닝(아래)이나 queueCapacity 하향을 먼저 검토할 것.
    // 대기 3s 는 application.yaml 의 종료 예산 합산(30 + 5 + 3 + 3 = 41s < 45s)에 들어간다.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(3);
    return executor;
  }
}
