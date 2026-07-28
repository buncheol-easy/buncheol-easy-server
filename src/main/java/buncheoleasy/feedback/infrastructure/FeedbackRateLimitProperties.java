package buncheoleasy.feedback.infrastructure;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import java.time.temporal.ChronoUnit;
import org.springframework.validation.annotation.Validated;

/**
 * 의견 보내기 도배 방지 설정.
 *
 * <p>{@code window} 는 단위 없는 값(예: {@code 600})이 들어와도 분으로 읽히도록 {@link DurationUnit} 을 붙였다 — Spring
 * 기본 단위는 밀리초라 {@code 600} 이 600ms 로 해석되면 제한이 사실상 사라진다.
 *
 * @param maxSubmissions 제출 주체(회원/IP) 1개당 윈도우 내 최대 제출 수
 * @param globalMaxSubmissions 전체 합산 윈도우 내 최대 제출 수. 제출 주체 키를 우회당해도 슬랙 도배량 자체를 묶는 백스톱이다
 * @param window 고정 윈도우 길이
 */
@Validated
@ConfigurationProperties(prefix = "app.feedback.rate-limit")
public record FeedbackRateLimitProperties(
    @Positive int maxSubmissions,
    @Positive int globalMaxSubmissions,
    @NotNull @DurationUnit(ChronoUnit.MINUTES) Duration window) {}
