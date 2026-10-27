package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginCommand;
import buncheoleasy.auth.application.SocialLoginResult;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.AgeRangeRefreshResult;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final List<OAuth2UserProfileExtractor> profileExtractors;
  private final SocialLoginService socialLoginService;
  private final RefreshTokenCookieFactory refreshTokenCookieFactory;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final KakaoApiClient kakaoApiClient;
  private final AgeRangeConsentRedirector ageRangeConsentRedirector;
  private final LoginFailureRedirector loginFailureRedirector;
  private final String loginCallbackUrl;

  public OAuth2LoginSuccessHandler(
      final List<OAuth2UserProfileExtractor> profileExtractors,
      final SocialLoginService socialLoginService,
      final RefreshTokenCookieFactory refreshTokenCookieFactory,
      final OAuth2AuthorizedClientService authorizedClientService,
      final KakaoApiClient kakaoApiClient,
      final AgeRangeConsentRedirector ageRangeConsentRedirector,
      final LoginFailureRedirector loginFailureRedirector,
      @Value("${app.frontend.login-callback-url}") final String loginCallbackUrl) {
    this.profileExtractors = profileExtractors;
    this.socialLoginService = socialLoginService;
    this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    this.authorizedClientService = authorizedClientService;
    this.kakaoApiClient = kakaoApiClient;
    this.ageRangeConsentRedirector = ageRangeConsentRedirector;
    this.loginFailureRedirector = loginFailureRedirector;
    this.loginCallbackUrl = loginCallbackUrl;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2AuthenticationToken oauthToken = getAuthenticationToken(authentication);
    if (KakaoAuthorizationRequestResolver.isAgeRangeConsentCallback(request)) {
      ageRangeConsentRedirector.redirect(response, refreshAgeRange(oauthToken));
      return;
    }
    SocialLoginResult loginResult;
    try {
      loginResult = login(oauthToken);
    } catch (RuntimeException exception) {
      loginFailureRedirector.redirect(response, toLoginFailureReason(exception));
      return;
    }
    // 여기부터는 실패로 돌려보내지 않는다 — 리프레시 쿠키가 붙은 실패 응답이 생긴다.
    TokenPair token = loginResult.token();
    response.addHeader(
        HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(token.refreshToken()).toString());

    // 액세스 토큰은 쿼리가 아니라 프래그먼트(#)로 넘긴다. 프래그먼트는 서버로 전송되지 않으므로
    // nginx/ALB 액세스 로그와 Referer 헤더에서 유효기간 2시간 JWT 원문이 사라진다.
    //
    // ⚠️ 이것만으로 클라이언트 측 유출까지 막히지는 않는다. PostHog $current_url / GA page_location 은
    // location.href 기반이라 해시를 포함하고, 페이지뷰는 초기 로드에 캡처되므로 프론트가 해시를 읽어
    // 지우기 전에 이미 전송된다. 브라우저 히스토리에도 남는다. 콜백 진입 즉시 해시를 읽고
    // history.replaceState 로 제거하는 후속 조치가 클라 쪽에 필요하다 (server#116 리뷰 참고).
    //
    // encodeFragment 는 현재 토큰(base64url + '.')과 '='·'&' 에 대해 no-op 이다 — RFC 3986 의
    // fragment 는 pchar 를 허용하고 pchar 에 sub-delims('='·'&')와 unreserved 가 포함된다.
    // 토큰 형식이 바뀌어 '%'·공백·'#' 이 섞이면 그때 자동으로 올바르게 이스케이프된다.
    //
    // signup=1 은 이번 로그인으로 계정을 새로 만들었다는 표시다(프로필 완료 여부와 별개).
    // 키는 클라 AUTH_SIGNUP_PARAM 과 짝이다.
    String fragment = "accessToken=" + token.accessToken();
    if (loginResult.newAccount()) {
      fragment += "&signup=1";
    }
    String redirectUrl =
        UriComponentsBuilder.fromUriString(loginCallbackUrl)
            .fragment(UriUtils.encodeFragment(fragment, StandardCharsets.UTF_8))
            .build()
            .toUriString();
    response.sendRedirect(redirectUrl);
  }

  private SocialLoginResult login(final OAuth2AuthenticationToken oauthToken) {
    OAuth2UserProfile profile = getUserProfile(oauthToken);

    List<ServiceTermAgreement> serviceTerms = List.of();
    if (profile.provider() == SocialProvider.KAKAO) {
      String kakaoAccessToken = loadKakaoAccessToken(oauthToken);
      if (kakaoAccessToken != null) {
        profile = enrichFromKakaoApi(profile, kakaoAccessToken);
        serviceTerms = fetchServiceTerms(kakaoAccessToken);
      }
    }

    return socialLoginService.login(
        new SocialLoginCommand(
            profile.provider().name(),
            profile.providerId(),
            profile.email(),
            profile.name(),
            profile.phoneNumber(),
            profile.ageRange(),
            profile.ageRangeWithdrawn(),
            serviceTerms));
  }

  /**
   * 새 회원인데 카카오가 이메일을 주지 않은 것은 사용자 쪽 사정이라 WARN 으로 끝낸다. 그 밖은 서버 고장일 수 있어 스택과 함께 ERROR 로 남긴다(알림
   * 대상). 로그 문구에는 회원 식별자·이메일을 넣지 않는다.
   */
  private LoginFailureRedirector.Reason toLoginFailureReason(final RuntimeException exception) {
    if (exception instanceof BusinessException businessException
        && businessException.getErrorCode() == ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED) {
      log.warn("소셜 로그인 거절: 사유={}", LoginFailureRedirector.Reason.EMAIL_REQUIRED.getValue());
      return LoginFailureRedirector.Reason.EMAIL_REQUIRED;
    }
    log.error(
        "소셜 로그인 처리 실패: 사유={}", LoginFailureRedirector.Reason.FAILED.getValue(), exception);
    return LoginFailureRedirector.Reason.FAILED;
  }

  /**
   * 연령대 추가 동의 결과를 반영한다. 연령대는 ID 토큰에 없어 카카오 API 로 조회한다. 반영 뒤 회원에게 연령대가 있을 때만 동의 완료로 돌려보내고, 그 밖에는
   * 모두 실패로 돌려보낸다 — 결과 화면이 오류 페이지가 되지 않도록 예외도 여기서 끝낸다.
   */
  private AgeRangeConsentRedirector.Result refreshAgeRange(
      final OAuth2AuthenticationToken oauthToken) {
    try {
      OAuth2UserProfile profile = getUserProfile(oauthToken);
      String kakaoAccessToken = loadKakaoAccessToken(oauthToken);
      if (kakaoAccessToken == null) {
        return AgeRangeConsentRedirector.Result.FAILED;
      }
      KakaoApiClient.KakaoUserInfo userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
      AgeRangeRefreshResult result =
          socialLoginService.refreshAgeRange(
              profile.provider().name(),
              profile.providerId(),
              userInfo.ageRange(),
              Boolean.TRUE.equals(userInfo.ageRangeNeedsAgreement()));
      return switch (result) {
        case PRESENT -> AgeRangeConsentRedirector.Result.AGREED;
        case ABSENT -> {
          log.warn("연령대 추가 동의 후에도 연령대가 없음: needsAgreement={}", userInfo.ageRangeNeedsAgreement());
          yield AgeRangeConsentRedirector.Result.FAILED;
        }
        case NO_MEMBER -> {
          log.warn("연령대 추가 동의 계정으로 가입한 회원이 없음");
          yield AgeRangeConsentRedirector.Result.FAILED;
        }
      };
    } catch (RuntimeException exception) {
      log.warn("연령대 추가 동의 반영 실패: reason={}", exception.getMessage());
      return AgeRangeConsentRedirector.Result.FAILED;
    }
  }

  /** 카카오 access token 조회. 실패하면 null — 일반 로그인은 보강 정보만 포기하고 계속한다. */
  private String loadKakaoAccessToken(final OAuth2AuthenticationToken oauthToken) {
    try {
      OAuth2AuthorizedClient authorizedClient =
          authorizedClientService.loadAuthorizedClient(
              oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
      return authorizedClient != null ? authorizedClient.getAccessToken().getTokenValue() : null;
    } catch (RuntimeException exception) {
      log.warn("카카오 access token 조회 실패: {}", exception.getMessage());
      return null;
    }
  }

  /**
   * 이름·전화번호·연령대가 클레임에 없으면 카카오 API 로 보강한다. 실패는 로깅만 하고 가입을 막지 않는다.
   *
   * <p>조기 반환 조건은 현재 항상 통과된다 — 카카오 ID 토큰에 세 클레임이 모두 실리는 경우가 없다. 방어적으로만 유지하며, 보강 조회는 연령대 동의
   * 철회(needs_agreement) 신호의 유일한 출처이기도 하다.
   */
  private OAuth2UserProfile enrichFromKakaoApi(
      final OAuth2UserProfile profile, final String kakaoAccessToken) {
    if (profile.name() != null && profile.phoneNumber() != null && profile.ageRange() != null) {
      return profile;
    }
    try {
      KakaoApiClient.KakaoUserInfo userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
      return profile.withUserInfo(
          userInfo.name(),
          userInfo.phoneNumber(),
          userInfo.ageRange(),
          Boolean.TRUE.equals(userInfo.ageRangeNeedsAgreement()));
    } catch (RuntimeException exception) {
      log.warn("카카오 사용자 정보 보강 조회 실패: {}", exception.getMessage());
      return profile;
    }
  }

  /** 간편가입 약관 동의 내역 조회. 실패는 로깅만 하고 가입을 막지 않는다. */
  private List<ServiceTermAgreement> fetchServiceTerms(final String kakaoAccessToken) {
    try {
      return kakaoApiClient.getServiceTerms(kakaoAccessToken);
    } catch (RuntimeException exception) {
      log.warn("카카오 약관 동의 내역 조회 실패: {}", exception.getMessage());
      return List.of();
    }
  }

  private OAuth2UserProfile getUserProfile(final OAuth2AuthenticationToken oauthToken) {
    String provider = oauthToken.getAuthorizedClientRegistrationId();
    return extractProfile(provider, oauthToken.getPrincipal());
  }

  private OAuth2AuthenticationToken getAuthenticationToken(final Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
      throw new BusinessException(ErrorCode.AUTH_UNSUPPORTED_AUTHENTICATION);
    }
    return oauthToken;
  }

  private OAuth2UserProfile extractProfile(final String provider, final OAuth2User principal) {
    for (OAuth2UserProfileExtractor extractor : profileExtractors) {
      if (extractor.supports(provider)) {
        try {
          return extractor.extract(principal);
        } catch (RuntimeException exception) {
          log.warn("소셜 프로필 추출 실패: provider={}, reason={}", provider, exception.getMessage());
          throw new BusinessException(ErrorCode.AUTH_SOCIAL_PROVIDER_UNSUPPORTED);
        }
      }
    }
    throw new BusinessException(ErrorCode.AUTH_SOCIAL_PROVIDER_UNSUPPORTED);
  }
}
