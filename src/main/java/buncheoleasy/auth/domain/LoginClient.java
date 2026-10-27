package buncheoleasy.auth.domain;

/**
 * 리프레시 토큰을 받은 곳. 웹과 앱은 리프레시 칸을 따로 쓴다 — 한 칸을 같이 쓰면 앱에서 로그인하는 순간 웹 세션의 리프레시가 덮여 웹이
 * 로그아웃된다(반대도 같다).
 */
public enum LoginClient {
  WEB,
  APP
}
