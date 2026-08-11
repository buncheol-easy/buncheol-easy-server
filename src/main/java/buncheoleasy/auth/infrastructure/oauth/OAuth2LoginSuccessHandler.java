package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginCommand;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

@Component
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final List<OAuth2UserProfileExtractor> profileExtractors;
  private final SocialLoginService socialLoginService;
  private final RefreshTokenCookieFactory refreshTokenCookieFactory;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final KakaoApiClient kakaoApiClient;
  private final String loginCallbackUrl;

  public OAuth2LoginSuccessHandler(
      final List<OAuth2UserProfileExtractor> profileExtractors,
      final SocialLoginService socialLoginService,
      final RefreshTokenCookieFactory refreshTokenCookieFactory,
      final OAuth2AuthorizedClientService authorizedClientService,
      final KakaoApiClient kakaoApiClient,
      @Value("${app.frontend.login-callback-url}") final String loginCallbackUrl) {
    this.profileExtractors = profileExtractors;
    this.socialLoginService = socialLoginService;
    this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    this.authorizedClientService = authorizedClientService;
    this.kakaoApiClient = kakaoApiClient;
    this.loginCallbackUrl = loginCallbackUrl;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2AuthenticationToken oauthToken = getAuthenticationToken(authentication);
    OAuth2UserProfile profile = getUserProfile(oauthToken);
    validateSocialEmail(profile.email());

    List<ServiceTermAgreement> serviceTerms = List.of();
    if (profile.provider() == SocialProvider.KAKAO) {
      String kakaoAccessToken = loadKakaoAccessToken(oauthToken);
      if (kakaoAccessToken != null) {
        profile = enrichFromKakaoApi(profile, kakaoAccessToken);
        serviceTerms = fetchServiceTerms(kakaoAccessToken);
      }
    }

    TokenPair token =
        socialLoginService.login(
            new SocialLoginCommand(
                profile.provider().name(),
                profile.providerId(),
                profile.email(),
                profile.name(),
                profile.phoneNumber(),
                profile.ageRange(),
                profile.ageRangeWithdrawn(),
                serviceTerms));
    // providerId(카카오 회원 고유 식별자)는 남기지 않는다 — 장애 대응으로 LOG_LEVEL_APP=DEBUG 를
    // 올리는 순간, 즉 로그를 여러 사람이 들여다보는 때에 매 로그인마다 외부 식별자가 적재된다.
    log.debug("OAuth2 로그인 성공: provider={}", profile.provider());

    response.addHeader(
        HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(token.refreshToken()).toString());

    String redirectUrl =
        UriComponentsBuilder.fromUriString(loginCallbackUrl)
            .queryParam("accessToken", token.accessToken())
            .build()
            .toUriString();
    response.sendRedirect(redirectUrl);
  }

  /** 카카오 access token 조회. 실패해도 로그인은 계속 진행한다(보강 정보만 포기). */
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

  private void validateSocialEmail(final String email) {
    if (email == null || email.isBlank()) {
      throw new BusinessException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
    }
  }
}
