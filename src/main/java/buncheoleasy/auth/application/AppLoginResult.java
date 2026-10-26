package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;

/** 앱 로그인 결과 — 앱 칸 토큰과, 이번 로그인으로 계정을 새로 만들었는지. */
public record AppLoginResult(TokenPair token, boolean newAccount) {}
