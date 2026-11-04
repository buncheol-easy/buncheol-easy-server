package buncheoleasy.auth.domain;

/** 앱 로그인 일회용 코드에 묶인 내용 — 누구로 로그인했고, 어느 앱(challenge)이 바꿀 수 있고, 이번에 새로 가입했는지. */
public record AppLoginGrant(Long userId, String challenge, boolean newAccount) {}
