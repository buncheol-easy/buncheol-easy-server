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
        return new OAuth2UserProfile(KAKAO, oidcUser.getSubject(), oidcUser.getEmail());
    }
}
