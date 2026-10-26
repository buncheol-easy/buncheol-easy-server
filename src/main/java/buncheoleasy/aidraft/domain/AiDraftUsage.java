package buncheoleasy.aidraft.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * AI 개최글의 사용량 — 1인 동시 1건 잠금과 하루 횟수. 횟수는 세 가지다.
 *
 * <ul>
 *   <li>시도 — 잠금을 지난 요청 전부. 돌려주지 않는다. AI 까지 가지 않는 실패(읽지 못하는 판매처 등)를 끝없이 반복하는 것을 묶는다.
 *   <li>생성 — 화면에 보이는 하루 한도. AI 까지 가지 못한 실패는 돌려준다.
 *   <li>합계 — 서비스 전체 하루 생성 수. 막지 않고 알림 기준으로만 쓴다.
 * </ul>
 *
 * <p>저장소 장애 시: 잠금·시도·생성·생성 조회는 {@code AI-005} 로 막는다(비용이 나가는 호출이라 세지 못하면 열지 않는다). 잠금 풀기와 횟수 돌려주기는
 * 장애를 삼킨다 — 원래 응답을 가리지 않게 하고, 잠금은 수명이 지나면 풀린다. 합계는 알림용이라 장애면 값 없이 지나간다.
 */
public interface AiDraftUsage {

  /** 잠금을 잡으면 풀 때 쓸 표를, 이미 잡혀 있으면 empty 를 돌려준다. */
  Optional<String> lock(Long userId, Duration ttl);

  /** 자기 표일 때만 푼다 — 수명이 지나 남이 새로 잡은 잠금을 지우지 않는다. */
  void unlock(Long userId, String token);

  long addAttempt(Long userId, AiDraftDay day);

  long addGeneration(Long userId, AiDraftDay day);

  void refundGeneration(Long userId, AiDraftDay day);

  long countGenerations(Long userId, AiDraftDay day);

  OptionalLong addDailyTotal(AiDraftDay day);
}
