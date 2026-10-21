package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;

/** 소셜 로그인 결과. newAccount 는 이번 로그인으로 계정을 새로 만들었는지다 — 프로필 완료(가입 완결) 여부와는 별개다. */
public record SocialLoginResult(TokenPair token, boolean newAccount) {}
