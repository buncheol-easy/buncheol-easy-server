package buncheoleasy.auth.application;

/** 앱 카카오 로그인 콜백이 앱 주소에 실어 보내는 일회용 코드. */
public record AppLoginCode(String code, boolean newAccount) {}
