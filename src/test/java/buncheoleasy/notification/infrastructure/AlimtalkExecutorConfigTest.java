package buncheoleasy.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.notification.application.AlimtalkNotificationListener;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.event.TransactionalEventListener;

@DisplayName("알림톡 발송 순서 보장 테스트")
class AlimtalkExecutorConfigTest {

  @Test
  @DisplayName("알림톡 executor 는 스레드가 하나다 — 늘리면 발송 순서가 깨진다")
  void 알림톡_executor_는_단일_스레드다() {
    ThreadPoolTaskExecutor executor = new AlimtalkExecutorConfig().alimtalkExecutor();

    // 한 트랜잭션이 이벤트를 둘 이상 발행하면 커밋 후 핸들러가 동시에 제출된다. 스레드가 하나여야
    // 제출 순서(= 이벤트 발행 순서)가 그대로 발송 순서가 된다.
    assertThat(executor.getCorePoolSize()).isEqualTo(1);
    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("모든 발송 핸들러가 알림톡 전용 executor 를 지정한다")
  void 모든_핸들러가_전용_executor_를_쓴다() {
    List<Method> handlers =
        Arrays.stream(AlimtalkNotificationListener.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(TransactionalEventListener.class))
            .toList();

    assertThat(handlers).as("발송 핸들러를 하나도 못 찾았다 — 테스트가 무력화된 것").isNotEmpty();
    // 새 핸들러를 @Async 만 붙여 추가하면 기본 applicationTaskExecutor(멀티 스레드)로 새어
    // 그 알림만 순서를 안 지킨다. 놓치기 쉬운 실수라 여기서 막는다.
    assertThat(handlers)
        .allSatisfy(
            method -> {
              Async async = method.getAnnotation(Async.class);
              assertThat(async).as("%s 에 @Async 가 없다", method.getName()).isNotNull();
              assertThat(async.value())
                  .as("%s 가 알림톡 전용 executor 를 지정하지 않았다", method.getName())
                  .isEqualTo(AlimtalkExecutorConfig.ALIMTALK_EXECUTOR);
            });
  }
}
