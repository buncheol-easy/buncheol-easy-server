package buncheoleasy.push.dto.request;

import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 앱 푸시 기기 등록 요청.
 *
 * @param token APNs 기기 토큰(hex). 서버는 소문자로 저장한다
 * @param platform 지금은 {@code IOS} 만
 * @param environment 개발 빌드면 {@code SANDBOX}, 스토어·TestFlight 빌드면 {@code PRODUCTION}
 */
public record RegisterPushDeviceRequest(
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64,200}$") String token,
    @NotNull PushPlatform platform,
    @NotNull PushEnvironment environment) {}
