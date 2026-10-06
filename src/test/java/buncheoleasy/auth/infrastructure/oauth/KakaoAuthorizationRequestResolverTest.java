package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@DisplayName("KakaoAuthorizationRequestResolver 단위 테스트")
class KakaoAuthorizationRequestResolverTest {

  private static final String LOGIN_PATH = "/oauth2/authorization/kakao";
  private static final String AGE_RANGE_CONSENT_PATH = "/oauth2/authorization/kakao/age-range";

  private static final ClientRegistration KAKAO =
      ClientRegistration.withRegistrationId("kakao")
          .clientId("test-client-id")
          .clientSecret("test-client-secret")
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
          .scope("openid")
          .authorizationUri("https://kauth.kakao.com/oauth/authorize")
          .tokenUri("https://kauth.kakao.com/oauth/token")
          .jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
          .build();

  private final KakaoAuthorizationRequestResolver resolver =
      new KakaoAuthorizationRequestResolver(new InMemoryClientRegistrationRepository(KAKAO));

  private OAuth2AuthorizationRequest resolve(final String path) {
    return resolver.resolve(new MockHttpServletRequest("GET", path));
  }

  private MultiValueMap<String, String> queryParams(final OAuth2AuthorizationRequest request) {
    return UriComponentsBuilder.fromUriString(request.getAuthorizationRequestUri())
        .build()
        .getQueryParams();
  }

  private String decodedScope(final OAuth2AuthorizationRequest request) {
    return UriUtils.decode(
        queryParams(request).getFirst(OAuth2ParameterNames.SCOPE), StandardCharsets.UTF_8);
  }

  private boolean isAgeRangeConsentCallback(final String state) {
    MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
    callback.setParameter(OAuth2ParameterNames.STATE, state);
    return KakaoAuthorizationRequestResolver.isAgeRangeConsentCallback(callback);
  }

  @Nested
  @DisplayName("일반 로그인")
  class LoginTest {

    @Test
    void 카카오에_scope_를_보내지_않는다() {
      // when
      OAuth2AuthorizationRequest request = resolve(LOGIN_PATH);

      // then
      assertThat(queryParams(request)).doesNotContainKey(OAuth2ParameterNames.SCOPE);
    }

    @Test
    void 저장되는_인가_요청은_openid_를_유지해_OIDC_검증_대상이_된다() {
      // when
      OAuth2AuthorizationRequest request = resolve(LOGIN_PATH);

      // then
      assertThat(request.getScopes()).containsExactly("openid");
      assertThat(queryParams(request)).containsKey(OidcParameterNames.NONCE);
      assertThat((String) request.getAttribute(OidcParameterNames.NONCE)).isNotBlank();
    }

    @Test
    void 콜백은_연령대_추가_동의로_판별되지_않는다() {
      // when
      OAuth2AuthorizationRequest request = resolve(LOGIN_PATH);

      // then
      assertThat(isAgeRangeConsentCallback(request.getState())).isFalse();
    }
  }

  @Nested
  @DisplayName("연령대 추가 동의")
  class AgeRangeConsentTest {

    @Test
    void openid_와_age_range_만_요청한다() {
      // when
      OAuth2AuthorizationRequest request = resolve(AGE_RANGE_CONSENT_PATH);

      // then
      assertThat(request.getScopes()).containsExactly("openid", "age_range");
      assertThat(decodedScope(request)).isEqualTo("openid age_range");
      assertThat(queryParams(request)).containsKey(OidcParameterNames.NONCE);
    }

    @Test
    void 콜백은_일반_로그인과_같은_Redirect_URI_로_받는다() {
      // when
      OAuth2AuthorizationRequest request = resolve(AGE_RANGE_CONSENT_PATH);

      // then
      assertThat(request.getRedirectUri()).isEqualTo("http://localhost/login/oauth2/code/kakao");
      assertThat((String) request.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))
          .isEqualTo("kakao");
    }

    @Test
    void 콜백은_state_로_연령대_추가_동의임을_판별한다() {
      // when
      OAuth2AuthorizationRequest request = resolve(AGE_RANGE_CONSENT_PATH);

      // then
      assertThat(isAgeRangeConsentCallback(request.getState())).isTrue();
    }

    @Test
    void 요청마다_state_가_다르다() {
      // when
      String first = resolve(AGE_RANGE_CONSENT_PATH).getState();
      String second = resolve(AGE_RANGE_CONSENT_PATH).getState();

      // then
      assertThat(first).isNotEqualTo(second);
    }
  }

  @Test
  void 인가_요청_경로가_아니면_null_을_돌려준다() {
    // when & then
    assertThat(resolve("/v1/buncheols")).isNull();
    assertThat(resolve("/oauth2/authorization/kakao/unknown")).isNull();
  }

  @Test
  void state_가_없는_콜백은_연령대_추가_동의가_아니다() {
    // given
    MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");

    // when & then
    assertThat(KakaoAuthorizationRequestResolver.isAgeRangeConsentCallback(callback)).isFalse();
  }
}
