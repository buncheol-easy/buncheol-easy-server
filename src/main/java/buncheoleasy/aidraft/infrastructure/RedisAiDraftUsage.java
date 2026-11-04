package buncheoleasy.aidraft.infrastructure;

import buncheoleasy.aidraft.domain.AiDraftDay;
import buncheoleasy.aidraft.domain.AiDraftUsage;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.infrastructure.redis.RedisFixedWindowCounter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 AI 개최글 사용량. 하루 횟수는 공용 고정 윈도우 카운터로 세고, 키에 한국 날짜를 넣어 자정에 새 키로 넘어간다.
 *
 * <p><b>fail-closed</b>: 잠금·시도·생성을 세지 못하면 막는다(AI-005). 의견 보내기·관리자 로그인은 Redis 장애 때 통과시키지만(fail-open) 여기는
 * 반대다 — 그 둘은 막으면 서비스 기능(의견 수집·입금확인)이 멈추는 쪽이 더 손해였고, 여기는 통과시키면 횟수 제한 없이 돈이 나가는 외부 AI 호출이 열린다. AI
 * 작성이 잠깐 안 되는 것은 개최자가 직접 쓰면 그만이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAiDraftUsage implements AiDraftUsage {

  private static final String KEY_PREFIX = "AI_DRAFT:";

  /** 자기 표일 때만 지운다. GET 과 DEL 을 따로 보내면 그 사이 수명이 다해 남이 새로 잡은 잠금을 지울 수 있다. */
  private static final RedisScript<Long> UNLOCK_IF_OWNER =
      RedisScript.of(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
          end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final RedisFixedWindowCounter counter;

  @Override
  public Optional<String> lock(final Long userId, final Duration ttl) {
    String token = UUID.randomUUID().toString();
    try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(userId), token, ttl);
      return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    } catch (DataAccessException e) {
      throw unavailable("잠금", e);
    }
  }

  @Override
  public void unlock(final Long userId, final String token) {
    try {
      redisTemplate.execute(UNLOCK_IF_OWNER, List.of(lockKey(userId)), token);
    } catch (DataAccessException e) {
      // 수명이 지나면 풀린다. 이미 나간 응답(성공·실패)을 가리지 않게 삼킨다.
      log.warn("AI 개최글 잠금 풀기 실패 - 수명이 지나면 풀린다 - userId={}", userId, e);
    }
  }

  @Override
  public long addAttempt(final Long userId, final AiDraftDay day) {
    try {
      return counter.increment(attemptKey(userId, day), day.keyTtl());
    } catch (DataAccessException e) {
      throw unavailable("시도 횟수", e);
    }
  }

  @Override
  public long addGeneration(final Long userId, final AiDraftDay day) {
    try {
      return counter.increment(generationKey(userId, day), day.keyTtl());
    } catch (DataAccessException e) {
      throw unavailable("생성 횟수", e);
    }
  }

  @Override
  public void refundGeneration(final Long userId, final AiDraftDay day) {
    try {
      counter.decrement(generationKey(userId, day));
    } catch (DataAccessException e) {
      // 돌려주지 못하면 그 사람의 오늘 한도가 한 번 줄 뿐이다. 원래 오류 응답을 가리지 않게 삼킨다.
      log.warn("AI 개최글 생성 횟수 돌려주기 실패 - userId={}", userId, e);
    }
  }

  @Override
  public long countGenerations(final Long userId, final AiDraftDay day) {
    try {
      return counter.count(generationKey(userId, day));
    } catch (DataAccessException e) {
      throw unavailable("생성 횟수 조회", e);
    }
  }

  @Override
  public OptionalLong addDailyTotal(final AiDraftDay day) {
    try {
      return OptionalLong.of(counter.increment(totalKey(day), day.keyTtl()));
    } catch (DataAccessException e) {
      // 알림 기준으로만 쓰는 값이라 장애면 이번 한 번 세지 않고 지나간다.
      log.warn("AI 개최글 하루 합계 세기 실패 - 알림만 영향", e);
      return OptionalLong.empty();
    }
  }

  private static BusinessException unavailable(final String what, final DataAccessException e) {
    log.warn("AI 개최글 {} 확인 실패 - 막는다(fail-closed)", what, e);
    return new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE, e);
  }

  private static String lockKey(final Long userId) {
    return KEY_PREFIX + "lock:" + userId;
  }

  private static String attemptKey(final Long userId, final AiDraftDay day) {
    return KEY_PREFIX + "attempt:" + day.date() + ":" + userId;
  }

  private static String generationKey(final Long userId, final AiDraftDay day) {
    return KEY_PREFIX + "generation:" + day.date() + ":" + userId;
  }

  private static String totalKey(final AiDraftDay day) {
    return KEY_PREFIX + "total:" + day.date();
  }
}
