package buncheoleasy.global.ratelimit;

import java.time.Duration;

/**
 * 고정 윈도우 카운터 기반 호출 제한. 고정 윈도우라 경계에서 최대 2배까지 통과할 수 있지만, 도배·무차별 대입의 <b>속도를 꺾는 것</b>이 목적이라 정밀도보다
 * 단순함을 택했다.
 *
 * <p>키 네임스페이스는 호출자가 접두사로 구분한다(예: {@code FB:}, {@code ADMIN_LOGIN:}). 한도·윈도우를 인자로 받으므로 용도마다 다른
 * 정책을 같은 구현으로 쓴다.
 */
public interface FixedWindowRateLimiter {

  /**
   * 키의 카운터를 1 올리고 한도 이내인지 판정한다.
   *
   * <p><b>fail-open 이 계약이다</b> — 저장소 장애로 카운트할 수 없으면 {@code true}(통과)를 돌려준다. 즉 {@code true} 는 "한도
   * 이내"가 아니라 "막을 이유를 확인하지 못했다"는 뜻이고, 이 제한은 <b>부가 방어선일 때만</b> 써야 한다. 통과가 곧 사고인 곳(인가 판정 등)에는
   * 쓰지 말 것.
   *
   * @param key 제한 주체 키 (호출자가 네임스페이스 접두사를 포함해 넘긴다)
   * @param limit 윈도우 내 허용 횟수
   * @param window 고정 윈도우 길이
   * @return 한도 이내이거나 판정 불가면 {@code true}, 초과가 확인되면 {@code false}
   */
  boolean tryAcquire(String key, int limit, Duration window);

  /** 카운터를 지운다. 성공적인 인증처럼 "정상 사용이 확인된" 시점에 실패 누적을 되돌리는 용도. */
  void reset(String key);
}
