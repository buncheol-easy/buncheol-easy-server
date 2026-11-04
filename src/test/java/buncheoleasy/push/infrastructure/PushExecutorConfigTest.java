package buncheoleasy.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.notification.application.InboxNotificationRecordedEvent;
import buncheoleasy.push.application.PushNotificationListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("PushExecutorConfig 테스트")
class PushExecutorConfigTest {

  private ThreadPoolTaskExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new PushExecutorConfig().pushExecutor();
    executor.initialize();
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  void 두_스레드_대기열_500_이고_종료_때_기다리지_않는다() {
    assertThat(executor.getCorePoolSize()).isEqualTo(2);
    assertThat(executor.getMaxPoolSize()).isEqualTo(2);
    assertThat(executor.getQueueCapacity()).isEqualTo(500);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("push-");
    // 기다리면 종료 예산(server.shutdown 주석)에 더해지고, awaitTermination 은 상한이 아니라 SIGKILL 까지 붙들 수 있다.
    assertThat(ReflectionTestUtils.getField(executor, "waitForTasksToCompleteOnShutdown"))
        .isEqualTo(false);
  }

  @Test
  void 대기열이_넘치면_버리고_제출한_스레드에서_돌리지_않는다() throws Exception {
    // 제출 스레드는 알림톡 단일 스레드다 — CallerRuns 면 거기서 애플을 불러 알림톡 줄이 막힌다.
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch busy = new CountDownLatch(2);
    for (int i = 0; i < 2; i++) {
      executor.execute(
          () -> {
            busy.countDown();
            awaitQuietly(release);
          });
    }
    assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i < 500; i++) {
      executor.execute(() -> awaitQuietly(release));
    }
    AtomicBoolean ranOnCaller = new AtomicBoolean(false);
    Thread caller = Thread.currentThread();

    assertThatCode(() -> executor.execute(() -> ranOnCaller.set(Thread.currentThread() == caller)))
        .doesNotThrowAnyException();

    assertThat(ranOnCaller).isFalse();
    assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(500);
    release.countDown();
  }

  @Test
  void 푸시_리스너는_전용_풀에서_돈다() throws Exception {
    Async async =
        PushNotificationListener.class
            .getMethod("onInboxNotificationRecorded", InboxNotificationRecordedEvent.class)
            .getAnnotation(Async.class);

    assertThat(async).isNotNull();
    assertThat(async.value()).isEqualTo(PushExecutorConfig.PUSH_EXECUTOR);
  }

  private static void awaitQuietly(final CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
