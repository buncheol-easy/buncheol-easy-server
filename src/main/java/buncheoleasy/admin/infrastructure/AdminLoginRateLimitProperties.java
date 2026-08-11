package buncheoleasy.admin.infrastructure;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * 관리자 로그인 무차별 대입 방지 설정.
 *
 * <p>{@code window} 는 단위 없는 값(예: {@code 10})이 들어와도 분으로 읽히도록 {@link DurationUnit} 을 붙였다 — Spring 기본
 * 단위는 밀리초라 {@code 10} 이 10ms 로 해석되면 제한이 사실상 사라진다.
 *
 * <p>IP 축 한도는 두지 않는다 — 브라우저 트래픽이 프론트 프록시를 거치면서 IP 가 하나로 수렴해 개인별 제한이 아니라 전역 예산이 되기 때문이다
 * ({@code AdminAuthService} javadoc 참고).
 *
 * @param maxAttemptsPerLoginId 로그인 ID 1개당 윈도우 내 최대 시도 수. 특정 계정을 노린 추측을 직접 막는다
 * @param window 고정 윈도우 길이
 */
@Validated
@ConfigurationProperties(prefix = "app.admin.login.rate-limit")
public record AdminLoginRateLimitProperties(
    @Positive int maxAttemptsPerLoginId,
    @NotNull @DurationUnit(ChronoUnit.MINUTES) Duration window) {}
