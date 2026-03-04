package buncheoleasy.auth.infrastructure.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

public interface OAuth2UserProfileExtractor {

  boolean supports(String registrationId);

  OAuth2UserProfile extract(OAuth2User principal);
}
