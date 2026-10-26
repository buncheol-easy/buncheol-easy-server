package buncheoleasy.auth.presentation;

import buncheoleasy.auth.infrastructure.oauth.AppLoginRedirector;
import buncheoleasy.auth.infrastructure.oauth.KakaoAuthorizationRequestResolver;
import buncheoleasy.auth.infrastructure.oauth.LoginFailureRedirector;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 카카오 로그인 시작 요청 중 카카오로 보내지 않은 것만 여기 온다 — 앱 주소가 설정되어 있고 challenge 가 형식에 맞으면 Spring Security 의 인가
 * 요청 필터({@link KakaoAuthorizationRequestResolver})가 먼저 카카오로 돌려보낸다.
 *
 * <ul>
 *   <li>앱 주소가 비어 있음(앱 로그인 꺼짐) — 돌려보낼 곳이 없으므로 400 AUTH-013.
 *   <li>challenge 형식 오류 — 인증 창이 오류 화면에 멈추지 않도록 앱 주소로 {@code error=invalid_request}.
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AppLoginStartController {

  private final AppLoginRedirector appLoginRedirector;

  @GetMapping(KakaoAuthorizationRequestResolver.APP_LOGIN_PATH)
  public ResponseEntity<Void> rejectAppLoginStart() {
    if (!appLoginRedirector.isEnabled()) {
      throw new BusinessException(ErrorCode.AUTH_APP_LOGIN_DISABLED);
    }
    String location = appLoginRedirector.errorUrl(LoginFailureRedirector.Reason.INVALID_REQUEST);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
  }
}
