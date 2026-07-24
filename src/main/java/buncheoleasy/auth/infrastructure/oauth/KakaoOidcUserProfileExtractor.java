package buncheoleasy.auth.infrastructure.oauth;

import static buncheoleasy.user.domain.SocialProvider.KAKAO;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class KakaoOidcUserProfileExtractor implements OAuth2UserProfileExtractor {

  @Override
  public boolean supports(final String registrationId) {
    return KAKAO.matched(registrationId);
  }

  @Override
  public OAuth2UserProfile extract(final OAuth2User principal) {
    if (!(principal instanceof OidcUser oidcUser)) {
      throw new BusinessException(ErrorCode.AUTH_SOCIAL_PROVIDER_UNSUPPORTED);
    }
    // 카카오 ID 토큰에는 이름·전화번호 클레임이 기본 포함되지 않는다 — 값이 있으면 쓰고,
    // 없으면 성공 핸들러에서 KakaoApiClient(/v2/user/me)로 보강 조회한다.
    String name = asText(oidcUser.getClaims().get("name"));
    String phoneNumber =
        KakaoPhoneNumberNormalizer.normalize(asText(oidcUser.getClaims().get("phone_number")));
    return new OAuth2UserProfile(
        KAKAO, oidcUser.getSubject(), oidcUser.getEmail(), name, phoneNumber);
  }

  private String asText(final Object claimValue) {
    return claimValue instanceof String text && !text.isBlank() ? text : null;
  }
}
