package buncheoleasy.auth.dto.response;

import buncheoleasy.auth.application.AppLoginResult;

/** signup 은 이번 로그인으로 계정을 새로 만들었는지다(웹 로그인 콜백의 signup=1 과 같은 뜻). */
public record AppLoginResponse(String accessToken, String refreshToken, boolean signup) {

  public static AppLoginResponse from(final AppLoginResult result) {
    return new AppLoginResponse(
        result.token().accessToken(), result.token().refreshToken(), result.newAccount());
  }
}
