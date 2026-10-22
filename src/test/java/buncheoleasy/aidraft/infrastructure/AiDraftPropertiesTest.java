package buncheoleasy.aidraft.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

@DisplayName("AiDraftProperties 단위 테스트")
class AiDraftPropertiesTest {

  private static final Duration CONNECT = Duration.ofSeconds(2);
  private static final Duration READ = Duration.ofSeconds(25);

  private static AiDraftProperties create(
      final boolean enabled, final String baseUrl, final String token) {
    return new AiDraftProperties(enabled, baseUrl, token, CONNECT, READ, 10, 30, 100, 4);
  }

  private static AiDraftProperties limits(
      final int daily, final int attempt, final int alert, final int concurrent) {
    return new AiDraftProperties(
        true, "http://ai-worker:8000", "token", CONNECT, READ, daily, attempt, alert, concurrent);
  }

  @Nested
  @DisplayName("기능 스위치")
  class UsableTest {

    @Test
    void 스위치와_주소와_토큰이_모두_있어야_쓸_수_있다() {
      assertThat(create(true, "http://ai-worker:8000", "token").usable()).isTrue();
    }

    @Test
    void 하나라도_없으면_쓸_수_없다() {
      assertThat(create(false, "http://ai-worker:8000", "token").usable()).isFalse();
      assertThat(create(true, "", "token").usable()).isFalse();
      assertThat(create(true, "http://ai-worker:8000", " ").usable()).isFalse();
      assertThat(create(true, null, "token").usable()).isFalse();
    }

    @Test
    void 주소_끝_슬래시는_떼어_낸다() {
      // 끝 슬래시가 남으면 //internal/drafts 로 불러 엔진이 404 를 준다.
      assertThat(create(true, "http://ai-worker:8000/", "token").workerBaseUrl())
          .isEqualTo("http://ai-worker:8000");
    }

    @Test
    void 빈_스위치_값은_꺼짐으로_읽힌다() {
      // .env.example 은 AI_DRAFT_ENABLED= 처럼 이름만 둔다. 그대로 복사해도 기동은 되고 꺼진 채여야 한다.
      Binder binder =
          new Binder(
              new MapConfigurationPropertySource(
                  Map.of(
                      "ai.draft.enabled", "",
                      "ai.draft.worker-base-url", "",
                      "ai.draft.worker-internal-token", "",
                      "ai.draft.connect-timeout", "2s",
                      "ai.draft.read-timeout", "25s",
                      "ai.draft.daily-limit-per-user", "10",
                      "ai.draft.daily-attempt-limit-per-user", "30",
                      "ai.draft.daily-alert-threshold", "100",
                      "ai.draft.max-concurrent", "4")));

      AiDraftProperties properties = binder.bind("ai.draft", AiDraftProperties.class).get();

      assertThat(properties.enabled()).isNotEqualTo(Boolean.TRUE);
      assertThat(properties.usable()).isFalse();
    }
  }

  @Nested
  @DisplayName("기동 시 검사")
  class FailFastTest {

    @Test
    void 한도와_동시_상한은_양수여야_한다() {
      assertThatThrownBy(() -> limits(0, 30, 100, 4)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> limits(10, 30, 0, 4)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> limits(10, 30, 100, -1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시도_상한이_생성_한도보다_작으면_실패한다() {
      // 화면에 보이는 한도를 다 쓰기 전에 숨은 상한에 막히면 "남은 횟수" 가 거짓이 된다.
      assertThatThrownBy(() -> limits(10, 9, 100, 4)).isInstanceOf(IllegalArgumentException.class);
      assertThat(limits(10, 10, 100, 4).dailyAttemptLimitPerUser()).isEqualTo(10);
    }

    @Test
    void 타임아웃은_양수여야_한다() {
      assertThatThrownBy(
              () ->
                  new AiDraftProperties(
                      true, "http://a", "t", Duration.ZERO, READ, 10, 30, 100, 4))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void 잠금_수명은_엔진_대기_전체보다_길다() {
    // 2s + 25s + 13s = 40s — 엔진을 기다리는 동안 같은 사람의 두 번째 요청이 잠금을 새로 잡지 못하게.
    assertThat(create(true, "http://a", "t").lockTtl()).isEqualTo(Duration.ofSeconds(40));
  }
}
