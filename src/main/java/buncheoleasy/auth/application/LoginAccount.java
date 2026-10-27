package buncheoleasy.auth.application;

/** 소셜 가입/로그인 처리 결과(토큰 발급 전). newAccount 는 이번 로그인으로 계정을 새로 만들었는지다. */
public record LoginAccount(Long userId, boolean newAccount) {}
