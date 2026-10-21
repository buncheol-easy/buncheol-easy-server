package buncheoleasy.user.domain;

/** 소셜 로그인으로 찾거나 만든 회원. */
public record SocialLoginUser(User user, boolean newAccount) {}
