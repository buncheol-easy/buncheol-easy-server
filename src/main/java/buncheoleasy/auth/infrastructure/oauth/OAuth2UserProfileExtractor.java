package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.auth.dto.OAuth2UserProfile;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface OAuth2UserProfileExtractor {

    boolean supports(String registrationId);

    OAuth2UserProfile extract(OAuth2User principal);
}
