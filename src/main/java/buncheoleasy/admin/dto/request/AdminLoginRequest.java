package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 ID/PW 로그인 요청. password 상한은 BCrypt 반영 한계(72바이트)에 맞췄다. */
public record AdminLoginRequest(
    @NotBlank @Size(max = 50) String loginId, @NotBlank @Size(max = 72) String password) {}
