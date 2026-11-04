package buncheoleasy.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 애플 로그인. fullName 은 애플이 첫 로그인 때만 앱에 주는 이름이다(없으면 null). 프로필 실명 규칙(한글·영문 1~30자, 공백 없음)에 맞지 않으면
 * 저장하지 않는다 — 가입 정보 화면에서 다시 받는다.
 */
public record AppleLoginRequest(
    @NotBlank @Size(max = 8192) String identityToken,
    @NotBlank @Size(max = 256) String rawNonce,
    @Size(max = 100) String fullName) {}
