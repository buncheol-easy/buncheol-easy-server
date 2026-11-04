package buncheoleasy.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 앱 카카오 로그인 일회용 코드 교환. verifier 는 RFC 7636 형식(43~128자, 영문·숫자·{@code -._~}). */
public record AppTokenRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._~-]{43,128}$") String verifier) {}
