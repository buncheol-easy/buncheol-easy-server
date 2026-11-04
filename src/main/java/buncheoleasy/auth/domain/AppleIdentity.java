package buncheoleasy.auth.domain;

/** 검증을 마친 애플 신원 토큰의 내용. subject 는 앱(팀)마다 고정된 애플 사용자 식별자, email 은 없을 수 있다. */
public record AppleIdentity(String subject, String email) {}
