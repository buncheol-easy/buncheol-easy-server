package buncheoleasy.auth.infrastructure.redis;

import buncheoleasy.auth.domain.AppLoginCodeStore;
import buncheoleasy.auth.domain.AppLoginGrant;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 앱 로그인 일회용 코드. 키 {@code APPCODE:{code}}, 값 {@code {userId}:{0|1 새 가입}:{challenge}}. 꺼낼 때 GETDEL(Redis 6.2+)
 * 한 번으로 읽기와 삭제를 같이 해, 같은 코드로 동시에 두 번 바꿔도 한쪽만 값을 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAppLoginCodeStore implements AppLoginCodeStore {

  private static final String KEY_PREFIX = "APPCODE:";
  private static final String SEPARATOR = ":";
  private static final String NEW_ACCOUNT = "1";
  private static final String EXISTING_ACCOUNT = "0";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void save(final String code, final AppLoginGrant grant, final Duration timeToLive) {
    String value =
        grant.userId()
            + SEPARATOR
            + (grant.newAccount() ? NEW_ACCOUNT : EXISTING_ACCOUNT)
            + SEPARATOR
            + grant.challenge();
    redisTemplate.opsForValue().set(KEY_PREFIX + code, value, timeToLive);
  }

  @Override
  public Optional<AppLoginGrant> consume(final String code) {
    String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
    if (value == null) {
      return Optional.empty();
    }
    return parse(value);
  }

  // challenge 는 base64url(콜론 없음)이라 세 조각으로 나뉜다. 모양이 다르면 쓰지 않는다 — 코드는 이미 지워졌다.
  private Optional<AppLoginGrant> parse(final String value) {
    String[] parts = value.split(SEPARATOR, 3);
    if (parts.length != 3 || !(NEW_ACCOUNT.equals(parts[1]) || EXISTING_ACCOUNT.equals(parts[1]))) {
      log.warn("앱 로그인 코드 값 형식이 다름: 조각 수={}", parts.length);
      return Optional.empty();
    }
    try {
      return Optional.of(
          new AppLoginGrant(Long.parseLong(parts[0]), parts[2], NEW_ACCOUNT.equals(parts[1])));
    } catch (NumberFormatException exception) {
      log.warn("앱 로그인 코드 값의 회원 번호가 숫자가 아님");
      return Optional.empty();
    }
  }
}
