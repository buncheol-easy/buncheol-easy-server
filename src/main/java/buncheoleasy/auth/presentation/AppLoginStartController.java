package buncheoleasy.auth.presentation;

import buncheoleasy.auth.infrastructure.oauth.AppLoginRedirector;
import buncheoleasy.auth.infrastructure.oauth.KakaoAuthorizationRequestResolver;
import buncheoleasy.auth.infrastructure.oauth.LoginFailureRedirector;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 카카오 로그인 시작 요청 중 challenge 가 형식에 맞지 않아 카카오로 보내지 않은 것만 여기 온다 — 형식이 맞으면 Spring Security 의 인가 요청
 * 필터({@link KakaoAuthorizationRequestResolver})가 먼저 카카오로 돌려보낸다. 인증 창이 오류 화면에 멈추지 않도록 앱 주소로 돌려보낸다.
 */
@RestController
@RequiredArgsConstructor
public class AppLoginStartController {

  private final AppLoginRedirector appLoginRedirector;

  @GetMapping(KakaoAuthorizationRequestResolver.APP_LOGIN_PATH)
  public ResponseEntity<Void> rejectInvalidChallenge() {
    String location = appLoginRedirector.errorUrl(LoginFailureRedirector.Reason.INVALID_REQUEST);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
  }
}
