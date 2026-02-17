package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.user.domain.SocialProvider;

public record OAuth2UserProfile(SocialProvider provider, String providerId, String email) {
}
