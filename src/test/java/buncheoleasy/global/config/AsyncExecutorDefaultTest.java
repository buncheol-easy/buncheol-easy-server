package buncheoleasy.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * executor 를 지정하지 않은 {@code @Async}(슬랙·입금·이미지·최근검색)가 어디서 도는지 고정하는 가드.
 *
 * <p>{@code applicationTaskExecutor} 자동설정은 {@code @ConditionalOnMissingBean(Executor.class)} 라,
 * 전용 풀(trackingExecutor·alimtalkExecutor)은 물론 {@code taskScheduler} 만 있어도 꺼진다. 그러면 무지정 {@code @Async} 가
 * {@code SimpleAsyncTaskExecutor}(호출마다 새 스레드·상한 없음)로 폴백하고, {@code spring.task.execution.shutdown.*} 도
 * 그 빈 전용이라 함께 무효가 된다 — 종료 드레인에서 알림이 통째로 빠진다.
 *
 * <p>실제로 이 상태가 <b>조용히</b> 성립해 있었다(스레드명을 보지 않으면 알 수 없다). {@code
 * spring.task.execution.mode: force} 한 줄이 이를 막는데, 프로퍼티가 사라지거나 이름이 바뀌면 다시 조용히 회귀하므로 여기서 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("무지정 @Async 기본 executor 가드")
class AsyncExecutorDefaultTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("applicationTaskExecutor 가 등록돼 있다 — 없으면 무지정 @Async 가 SimpleAsyncTaskExecutor 로 샌다")
  void applicationTaskExecutor_가_등록된다() {
    assertThat(context.containsBean("applicationTaskExecutor"))
        .as("spring.task.execution.mode=force 가 빠졌거나 무효화됐다")
        .isTrue();
  }
}
