package buncheoleasy.auth.infrastructure.oauth;

import static org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;

import buncheoleasy.auth.domain.Pkce;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * 카카오 인가 요청을 일반 로그인과 연령대 추가 동의로 나눠 만든다.
 *
 * <p>일반 로그인은 카카오에 scope 를 보내지 않는다. 카카오는 scope 에 적힌 항목 중 기존 회원이 동의하지 않은 것을 추가 동의로 처리해 필수처럼
 * 띄운다. scope 가 없으면 동의 화면은 콘솔 동의항목 설정(필수/선택)을 따르고, OIDC 를 켠 앱이라 ID 토큰도 그대로 발급된다. 등록 scope(openid)는
 * Spring 이 OIDC 로 처리(ID 토큰·nonce 검증)하도록 남겨 둔다.
 *
 * <p>연령대 추가 동의는 openid·age_range 만 요청한다. scope 를 보내는 요청에서 openid 가 빠지면 카카오가 ID 토큰을 주지 않는다. 콜백 경로는 일반
 * 로그인과 같으므로(콘솔에 등록된 Redirect URI) 어느 흐름의 콜백인지는 state 접두사로 가른다.
 *
 * <p>앱 로그인({@code /oauth2/authorization/kakao/app?challenge=})은 일반 로그인과 같은 인가 요청에 state 만 {@code
 * app.<challenge>.<무작위>} 로 바꾼다. 성공 콜백의 state 는 Spring 이 세션에 저장한 인가 요청과 대조를 마친 값이라, 콜백에서 꺼낸
 * challenge 는 앱이 시작할 때 보낸 그 값이다. challenge 가 형식에 맞지 않으면 null 을 돌려 카카오로 보내지 않는다 — 그 요청은 {@code
 * AppLoginStartController} 가 받아 앱 주소로 오류를 돌려준다.
 */
@Component
public class KakaoAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  static final String AGE_RANGE_CONSENT_STATE_PREFIX = "age_range.";
  private static final String APP_LOGIN_STATE_PREFIX = "app.";
  public static final String APP_LOGIN_PATH = DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/kakao/app";
  private static final String APP_LOGIN_CHALLENGE_PARAM = "challenge";
  private static final char STATE_SEPARATOR = '.';

  private static final String AGE_RANGE_CONSENT_PATH =
      DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/kakao/age-range";
  private static final String KAKAO_REGISTRATION_ID = "kakao";
  private static final Set<String> AGE_RANGE_CONSENT_SCOPES =
      Collections.unmodifiableSet(new LinkedHashSet<>(List.of(OidcScopes.OPENID, "age_range")));
  private static final StringKeyGenerator STATE_GENERATOR =
      new Base64StringKeyGenerator(Base64.getUrlEncoder());

  private final RequestMatcher ageRangeConsentMatcher =
      PathPatternRequestMatcher.withDefaults().matcher(AGE_RANGE_CONSENT_PATH);
  private final RequestMatcher appLoginMatcher =
      PathPatternRequestMatcher.withDefaults().matcher(APP_LOGIN_PATH);
  private final DefaultOAuth2AuthorizationRequestResolver loginResolver;
  private final DefaultOAuth2AuthorizationRequestResolver ageRangeConsentResolver;

  public KakaoAuthorizationRequestResolver(
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.loginResolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    this.loginResolver.setAuthorizationRequestCustomizer(
        builder -> builder.parameters(params -> params.remove(OAuth2ParameterNames.SCOPE)));

    this.ageRangeConsentResolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    this.ageRangeConsentResolver.setAuthorizationRequestCustomizer(
        builder ->
            builder
                .scopes(AGE_RANGE_CONSENT_SCOPES)
                .state(AGE_RANGE_CONSENT_STATE_PREFIX + STATE_GENERATOR.generateKey()));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
    if (ageRangeConsentMatcher.matches(request)) {
      return ageRangeConsentResolver.resolve(request, KAKAO_REGISTRATION_ID);
    }
    if (appLoginMatcher.matches(request)) {
      return resolveAppLogin(request);
    }
    return loginResolver.resolve(request);
  }

  // 일반 로그인 요청을 만든 뒤 state 만 바꾼다. from() 은 scope 를 지우던 customizer 를 옮기지 않아 다시 지운다.
  private OAuth2AuthorizationRequest resolveAppLogin(final HttpServletRequest request) {
    String challenge = request.getParameter(APP_LOGIN_CHALLENGE_PARAM);
    if (!Pkce.isValidChallenge(challenge)) {
      return null;
    }
    OAuth2AuthorizationRequest login = loginResolver.resolve(request, KAKAO_REGISTRATION_ID);
    return OAuth2AuthorizationRequest.from(login)
        .state(
            APP_LOGIN_STATE_PREFIX + challenge + STATE_SEPARATOR + STATE_GENERATOR.generateKey())
        .parameters(params -> params.remove(OAuth2ParameterNames.SCOPE))
        .build();
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      final HttpServletRequest request, final String clientRegistrationId) {
    return loginResolver.resolve(request, clientRegistrationId);
  }

  /**
   * 콜백이 연령대 추가 동의 흐름인지 판별한다. 성공 콜백의 state 는 Spring 이 저장된 인가 요청과 대조를 마친 값이고, 실패 콜백에서는 돌려보낼 화면만
   * 정하므로 위조돼도 영향이 없다.
   */
  public static boolean isAgeRangeConsentCallback(final HttpServletRequest request) {
    String state = request.getParameter(OAuth2ParameterNames.STATE);
    return state != null && state.startsWith(AGE_RANGE_CONSENT_STATE_PREFIX);
  }

  /** 콜백이 앱 로그인 흐름인지 판별한다. 실패 콜백에서는 돌려보낼 곳만 정하므로 위조돼도 토큰이 새지 않는다. */
  public static boolean isAppLoginCallback(final HttpServletRequest request) {
    String state = request.getParameter(OAuth2ParameterNames.STATE);
    return state != null && state.startsWith(APP_LOGIN_STATE_PREFIX);
  }

  /** 앱 로그인 성공 콜백의 state 에서 challenge 를 꺼낸다. 모양이 다르면 비어 있다. */
  public static Optional<String> appLoginChallenge(final HttpServletRequest request) {
    String state = request.getParameter(OAuth2ParameterNames.STATE);
    if (state == null || !state.startsWith(APP_LOGIN_STATE_PREFIX)) {
      return Optional.empty();
    }
    String rest = state.substring(APP_LOGIN_STATE_PREFIX.length());
    int end = rest.indexOf(STATE_SEPARATOR);
    String challenge = end < 0 ? rest : rest.substring(0, end);
    return Pkce.isValidChallenge(challenge) ? Optional.of(challenge) : Optional.empty();
  }
}
