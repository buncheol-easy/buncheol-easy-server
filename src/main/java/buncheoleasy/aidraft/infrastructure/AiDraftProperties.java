package buncheoleasy.aidraft.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 개최글 도우미 설정. 기본은 꺼짐이다 — {@code enabled} 를 켜고 엔진 주소·내부 토큰을 모두 넣어야 동작한다(배송비 환급의 명시 스위치 + 배송 추적의
 * 키 유무를 합친 방식).
 *
 * @param enabled 기능 스위치. 래퍼 타입인 것은 의도다 — {@code .env} 에 {@code AI_DRAFT_ENABLED=} 처럼 값 없이 두면 빈 문자열이 들어오는데,
 *     기본형 boolean 은 그 값을 바인딩하지 못해 기동이 실패한다. 래퍼는 null(= 꺼짐)로 받는다
 * @param workerBaseUrl 엔진 주소 (예: {@code http://ai-worker:8000}). 끝 {@code /} 는 떼어 낸다
 * @param workerInternalToken 엔진 {@code INTERNAL_TOKEN} 과 같은 값. 헤더 {@code X-Internal-Token} 으로만 보낸다
 * @param connectTimeout 엔진 연결 대기
 * @param readTimeout 엔진 응답 대기. 엔진 최대 실측(13초)보다 넉넉히
 * @param dailyLimitPerUser 1인 하루 생성 한도(화면에 보이는 수)
 * @param dailyAttemptLimitPerUser 1인 하루 시도 상한(숨김). AI 까지 가지 않은 실패도 센다
 * @param dailyAlertThreshold 서비스 하루 합계가 이 값이 되는 순간 슬랙 알림 1회. 막지 않는다
 * @param maxConcurrent 이 서버(JVM)가 엔진을 동시에 부르는 상한
 */
@Validated
@ConfigurationProperties(prefix = "ai.draft")
public record AiDraftProperties(
    Boolean enabled,
    String workerBaseUrl,
    String workerInternalToken,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    int dailyLimitPerUser,
    int dailyAttemptLimitPerUser,
    int dailyAlertThreshold,
    int maxConcurrent) {

  /** 엔진 대기가 끝난 뒤에도 그룹 찾기·기록 마감이 남아 잠금이 먼저 풀리지 않게 하는 여유. */
  private static final Duration LOCK_MARGIN = Duration.ofSeconds(13);

  public AiDraftProperties {
    // 0·음수는 한도·동시 상한을 조용히 "전부 막기" 나 "무한" 으로 바꾼다 — 기동 시점에 fail-fast.
    requirePositive("ai.draft.daily-limit-per-user", dailyLimitPerUser);
    requirePositive("ai.draft.daily-attempt-limit-per-user", dailyAttemptLimitPerUser);
    requirePositive("ai.draft.daily-alert-threshold", dailyAlertThreshold);
    requirePositive("ai.draft.max-concurrent", maxConcurrent);
    if (dailyAttemptLimitPerUser < dailyLimitPerUser) {
      // 시도 상한이 더 작으면 화면에 보이는 한도를 다 쓰기 전에 막혀, 남은 횟수 표시가 거짓이 된다.
      throw new IllegalArgumentException(
          "ai.draft.daily-attempt-limit-per-user 는 daily-limit-per-user 이상이어야 한다: "
              + dailyAttemptLimitPerUser
              + " < "
              + dailyLimitPerUser);
    }
    if (connectTimeout != null && !connectTimeout.isPositive()
        || readTimeout != null && !readTimeout.isPositive()) {
      throw new IllegalArgumentException("ai.draft 타임아웃은 양수여야 한다");
    }
    if (workerBaseUrl != null && workerBaseUrl.endsWith("/")) {
      workerBaseUrl = workerBaseUrl.substring(0, workerBaseUrl.length() - 1);
    }
  }

  /** 스위치가 켜져 있고 엔진 주소·토큰이 모두 있을 때만 쓸 수 있다. */
  public boolean usable() {
    return Boolean.TRUE.equals(enabled) && isSet(workerBaseUrl) && isSet(workerInternalToken);
  }

  /** 1인 잠금 수명 — 엔진 대기 전체보다 길어야 같은 사람의 두 번째 요청이 겹치지 않는다. */
  public Duration lockTtl() {
    return connectTimeout.plus(readTimeout).plus(LOCK_MARGIN);
  }

  private static void requirePositive(final String name, final int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " 는 양수여야 한다: " + value);
    }
  }

  private static boolean isSet(final String value) {
    return value != null && !value.isBlank();
  }
}
