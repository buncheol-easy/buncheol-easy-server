package buncheoleasy.aidraft.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.aidraft.domain.AiDraftDay;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.infrastructure.redis.RedisFixedWindowCounter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** 실제 공용 카운터를 거쳐 Redis 템플릿까지 내려가는 키·수명·장애 처리를 고정한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAiDraftUsage 단위 테스트")
class RedisAiDraftUsageTest {

  private static final Long USER_ID = 7L;
  // 2026-10-22 23:00 KST — 자정까지 1시간, 키 수명 2시간
  private static final AiDraftDay DAY = AiDraftDay.of(Instant.parse("2026-10-22T14:00:00Z"));
  private static final String TTL_MILLIS = String.valueOf(Duration.ofHours(2).toMillis());

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisAiDraftUsage usage;

  @BeforeEach
  void setUp() {
    usage = new RedisAiDraftUsage(redisTemplate, new RedisFixedWindowCounter(redisTemplate));
  }

  @SuppressWarnings("unchecked")
  private void givenIncrementFails() {
    given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willThrow(new RedisConnectionFailureException("redis down"));
  }

  private static void assertUnavailable(final Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_DRAFT_UNAVAILABLE);
  }

  @Nested
  @DisplayName("1인 잠금")
  class LockTest {

    @Test
    void 비어_있으면_수명을_걸고_표를_돌려준다() {
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(
              valueOperations.setIfAbsent(
                  eq("AI_DRAFT:lock:7"), anyString(), eq(Duration.ofSeconds(40))))
          .willReturn(true);

      Optional<String> token = usage.lock(USER_ID, Duration.ofSeconds(40));

      assertThat(token).isPresent();
    }

    @Test
    void 이미_잡혀_있으면_빈_값이다() {
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
          .willReturn(false);

      assertThat(usage.lock(USER_ID, Duration.ofSeconds(40))).isEmpty();
    }

    @Test
    void 잡을_때_Redis_장애면_막는다() {
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
          .willThrow(new RedisConnectionFailureException("redis down"));

      assertUnavailable(() -> usage.lock(USER_ID, Duration.ofSeconds(40)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 풀_때는_자기_표일_때만_지우는_스크립트를_쓴다() {
      usage.unlock(USER_ID, "my-token");

      ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
      then(redisTemplate)
          .should()
          .execute(script.capture(), eq(List.of("AI_DRAFT:lock:7")), eq("my-token"));
      assertThat(script.getValue().getScriptAsString())
          .contains("redis.call('GET', KEYS[1]) == ARGV[1]")
          .contains("redis.call('DEL', KEYS[1])");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 풀_때_Redis_장애는_삼킨다() {
      // 수명이 지나면 풀린다. 이미 정해진 응답을 잠금 풀기 실패로 바꾸지 않는다.
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
          .willThrow(new RedisConnectionFailureException("redis down"));

      assertThatCode(() -> usage.unlock(USER_ID, "my-token")).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("하루 횟수")
  class DailyCountTest {

    @Test
    @SuppressWarnings("unchecked")
    void 시도와_생성은_한국_날짜와_회원이_든_키로_다음_자정_뒤까지_센다() {
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
          .willReturn(3L, 2L);

      assertThat(usage.addAttempt(USER_ID, DAY)).isEqualTo(3L);
      assertThat(usage.addGeneration(USER_ID, DAY)).isEqualTo(2L);

      then(redisTemplate)
          .should()
          .execute(any(RedisScript.class), eq(List.of("AI_DRAFT:attempt:2026-10-22:7")), eq(TTL_MILLIS));
      then(redisTemplate)
          .should()
          .execute(
              any(RedisScript.class), eq(List.of("AI_DRAFT:generation:2026-10-22:7")), eq(TTL_MILLIS));
    }

    @Test
    void 시도_생성을_세지_못하면_막는다() {
      // 돈이 나가는 호출이라 세지 못하면 열지 않는다(fail-closed) — 의견 보내기·관리자 로그인과 반대.
      givenIncrementFails();

      assertUnavailable(() -> usage.addAttempt(USER_ID, DAY));
      assertUnavailable(() -> usage.addGeneration(USER_ID, DAY));
    }

    @Test
    void 남은_횟수_조회는_생성_키를_읽고_장애면_막는다() {
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("AI_DRAFT:generation:2026-10-22:7")).willReturn("4");

      assertThat(usage.countGenerations(USER_ID, DAY)).isEqualTo(4L);

      given(valueOperations.get(anyString()))
          .willThrow(new RedisConnectionFailureException("redis down"));
      assertUnavailable(() -> usage.countGenerations(USER_ID, DAY));
    }

    @Test
    void 생성_돌려주기는_같은_날_키를_내린다() {
      usage.refundGeneration(USER_ID, DAY);

      then(redisTemplate)
          .should()
          .execute(any(RedisScript.class), eq(List.of("AI_DRAFT:generation:2026-10-22:7")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 생성_돌려주기_장애는_삼킨다() {
      given(redisTemplate.execute(any(RedisScript.class), anyList()))
          .willThrow(new RedisConnectionFailureException("redis down"));

      assertThatCode(() -> usage.refundGeneration(USER_ID, DAY)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("하루 합계")
  class DailyTotalTest {

    @Test
    @SuppressWarnings("unchecked")
    void 회원과_무관한_날짜_키로_센다() {
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(100L);

      assertThat(usage.addDailyTotal(DAY)).hasValue(100L);
      then(redisTemplate)
          .should()
          .execute(any(RedisScript.class), eq(List.of("AI_DRAFT:total:2026-10-22")), eq(TTL_MILLIS));
    }

    @Test
    void 장애면_막지_않고_값_없이_지나간다() {
      givenIncrementFails();

      assertThat(usage.addDailyTotal(DAY)).isEmpty();
    }
  }
}
