package buncheoleasy.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.AppLoginGrant;
import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis 7(운영 compose 와 같은 이미지)에서 앱 로그인 저장소가 약속대로 움직이는지 본다 — 목으로는 GETDEL 의 원자성·TTL 만료·칸별 키가
 * 실제로 갈리는지를 확인할 수 없다. Docker 가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("앱 로그인 Redis 저장소 실측 테스트 (Redis 7)")
class RedisAuthStoresContainerTest {

  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  @BeforeAll
  static void connect() {
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redisTemplate = new StringRedisTemplate(connectionFactory);
  }

  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void flush() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Nested
  @DisplayName("일회용 코드")
  class AppLoginCodeTest {

    private final RedisAppLoginCodeStore store = new RedisAppLoginCodeStore(redisTemplate);

    @Test
    void 한_번_꺼내면_두_번째는_비어_있다() {
      // given
      store.save("code-1", new AppLoginGrant(7L, CHALLENGE, true), Duration.ofSeconds(120));

      // when & then
      assertThat(store.consume("code-1")).contains(new AppLoginGrant(7L, CHALLENGE, true));
      assertThat(store.consume("code-1")).isEmpty();
      assertThat(redisTemplate.hasKey("APPCODE:code-1")).isFalse();
    }

    @Test
    void TTL_이_지나면_비어_있다() throws InterruptedException {
      // given
      store.save("code-2", new AppLoginGrant(7L, CHALLENGE, false), Duration.ofMillis(300));

      // when
      Thread.sleep(600);

      // then
      assertThat(store.consume("code-2")).isEmpty();
    }

    @Test
    void 저장한_TTL_이_Redis_에_걸린다() {
      // when
      store.save("code-3", new AppLoginGrant(7L, CHALLENGE, false), Duration.ofSeconds(120));

      // then
      assertThat(redisTemplate.getExpire("APPCODE:code-3")).isBetween(110L, 120L);
    }

    @Test
    void 같은_코드를_동시에_꺼내도_한쪽만_받는다() throws Exception {
      // given
      store.save("code-4", new AppLoginGrant(7L, CHALLENGE, false), Duration.ofSeconds(120));
      int threads = 8;
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Optional<AppLoginGrant>>> results = new ArrayList<>();
      Callable<Optional<AppLoginGrant>> consume =
          () -> {
            start.await();
            return store.consume("code-4");
          };

      // when
      try {
        for (int i = 0; i < threads; i++) {
          results.add(executor.submit(consume));
        }
        start.countDown();

        // then
        int received = 0;
        for (Future<Optional<AppLoginGrant>> result : results) {
          received += result.get().isPresent() ? 1 : 0;
        }
        assertThat(received).isEqualTo(1);
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Nested
  @DisplayName("리프레시 칸 — 실제 키로 웹·앱이 갈리는지")
  class RefreshSlotTest {

    private final RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
    private final JwtTokenProvider provider =
        new JwtTokenProvider(
            "test-access-secret-test-access-secret-test-access-secret",
            "test-refresh-secret-test-refresh-secret-test-refresh-secret",
            3600,
            1209600,
            43200,
            900,
            store);

    @Test
    void 웹_칸_키는_앱_칸이_생기기_전과_같다() {
      // when
      TokenPair web = provider.issueTokens(7L);
      TokenPair app = provider.issueTokens(7L, LoginClient.APP);

      // then
      assertThat(redisTemplate.opsForValue().get("RT:7")).isEqualTo(web.refreshToken());
      assertThat(redisTemplate.opsForValue().get("RT:APP:7")).isEqualTo(app.refreshToken());
    }

    @Test
    void 앱_로그인과_앱_로그아웃이_웹_세션을_끊지_않는다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();

      // when — 앱 로그인 후 앱 로그아웃
      provider.issueTokens(7L, LoginClient.APP);
      store.delete(7L, LoginClient.APP);

      // then
      assertThatCode(() -> provider.reissueTokens(7L, webRefresh)).doesNotThrowAnyException();
      assertThat(redisTemplate.hasKey("RT:APP:7")).isFalse();
    }

    @Test
    void 웹_로그인이_앱_세션을_끊지_않는다() {
      // given
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when
      provider.issueTokens(7L);

      // then
      assertThatCode(() -> provider.reissueTokens(7L, LoginClient.APP, appRefresh))
          .doesNotThrowAnyException();
    }

    @Test
    void 모든_칸_삭제_탈퇴는_웹과_앱을_다_끊는다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when
      store.deleteAll(7L);

      // then
      assertThatThrownBy(() -> provider.reissueTokens(7L, webRefresh))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> provider.reissueTokens(7L, LoginClient.APP, appRefresh))
          .isInstanceOf(BusinessException.class);
      assertThat(redisTemplate.hasKey("RT:7")).isFalse();
      assertThat(redisTemplate.hasKey("RT:APP:7")).isFalse();
    }
  }
}
