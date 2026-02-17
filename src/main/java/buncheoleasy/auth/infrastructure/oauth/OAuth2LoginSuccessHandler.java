package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.infrastructure.response.TokenResponseWriter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final List<OAuth2UserProfileExtractor> profileExtractors;
    private final SocialLoginService socialLoginService;
    private final TokenResponseWriter tokenResponseWriter;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = getAuthenticationToken(authentication);
        OAuth2UserProfile profile = getUserProfile(oauthToken);

        TokenPair token = socialLoginService.login(
                profile.provider().name(),
                profile.providerId(),
                profile.email()
        );
        log.debug("OAuth2 로그인 성공: provider={}, providerId={}", profile.provider(), profile.providerId());
        tokenResponseWriter.write(response, token);
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
