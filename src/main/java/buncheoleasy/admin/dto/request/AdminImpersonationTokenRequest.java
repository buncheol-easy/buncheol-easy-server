package buncheoleasy.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 impersonation 토큰 발급 요청. 유저 계정에 대신 접속하는 민감 동작이라 사유(reason)를 필수로 받아 감사 로그에 남긴다 — 어떤 문의를
 * 재현하려 했는지 추적할 수 있어야 한다.
 */
public record AdminImpersonationTokenRequest(@NotBlank @Size(max = 200) String reason) {}
