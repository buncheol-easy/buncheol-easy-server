package buncheoleasy.admin.dto.response;

/**
 * 관리자 impersonation 토큰 발급 응답. accessToken 은 대상 유저의 유저 토큰(ROLE_USER)이며 짧은 수명이다. 프론트는 이 토큰으로
 * 유저 세션을 재현한다 — 만료되면(expiresInSeconds) 관리자가 다시 발급한다.
 */
public record AdminImpersonationTokenResponse(
    Long targetUserId, String accessToken, long expiresInSeconds) {}
