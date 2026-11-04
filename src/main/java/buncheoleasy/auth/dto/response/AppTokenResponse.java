package buncheoleasy.auth.dto.response;

import buncheoleasy.auth.TokenPair;

public record AppTokenResponse(String accessToken, String refreshToken) {

  public static AppTokenResponse from(final TokenPair token) {
    return new AppTokenResponse(token.accessToken(), token.refreshToken());
  }
}
