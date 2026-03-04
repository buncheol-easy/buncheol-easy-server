package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
@DisplayName("KakaoOidcUserProfileExtractor 단위 테스트")
class KakaoOidcUserProfileExtractorTest {

  @Mock private OidcUser oidcUser;

  @Mock private OAuth2User oauth2User;

  @Nested
  @DisplayName("supports 테스트")
  class SupportsTest {

    @Test
    void registrationId가_kakao이면_true를_반환한다() {
      KakaoOidcUserProfileExtractor extractor = new KakaoOidcUserProfileExtractor();

      assertThat(extractor.supports("kakao")).isTrue();
    }

    @Test
    void registrationId가_kakao가_아니면_false를_반환한다() {
      KakaoOidcUserProfileExtractor extractor = new KakaoOidcUserProfileExtractor();

      assertThat(extractor.supports("google")).isFalse();
    }
  }

  @Nested
  @DisplayName("extract 테스트")
  class ExtractTest {

    @Test
    void OidcUser에서_프로필을_추출한다() {
      // given
      KakaoOidcUserProfileExtractor extractor = new KakaoOidcUserProfileExtractor();
      given(oidcUser.getSubject()).willReturn("provider-id");
      given(oidcUser.getEmail()).willReturn("test@example.com");

      // when
      OAuth2UserProfile profile = extractor.extract(oidcUser);

      // then
      assertThat(profile.provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(profile.providerId()).isEqualTo("provider-id");
      assertThat(profile.email()).isEqualTo("test@example.com");
    }

    @Test
    void OidcUser가_아니면_예외가_발생한다() {
      // given
      KakaoOidcUserProfileExtractor extractor = new KakaoOidcUserProfileExtractor();

      // when & then
      assertThatThrownBy(() -> extractor.extract(oauth2User))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_SOCIAL_PROVIDER_UNSUPPORTED);
    }
  }
}
