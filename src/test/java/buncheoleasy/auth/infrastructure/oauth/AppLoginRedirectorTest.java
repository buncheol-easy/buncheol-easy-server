package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("AppLoginRedirector 단위 테스트")
class AppLoginRedirectorTest {

  @Nested
  @DisplayName("앱 주소 설정")
  class ConfigurationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 비어_있으면_꺼진다(final String value) {
      assertThat(new AppLoginRedirector(value).isEnabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://buncheoleasy.com/login/app",
          " https://staging.buncheoleasy.com/login/app ",
          "HTTPS://buncheoleasy.com/login/app"
        })
    void https_절대_주소면_켜진다(final String value) {
      assertThat(new AppLoginRedirector(value).isEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "/login/app",
          "auth",
          "https:login/app",
          "https:///login/app",
          "https://buncheoleasy.com/login/app#frag",
          "bad url://x"
        })
    void 스킴_없는_주소_호스트_없는_주소_조각이_붙은_주소는_기동에서_막는다(final String value) {
      assertThatThrownBy(() -> new AppLoginRedirector(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("APP_LOGIN_CALLBACK_URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"buncheoleasy://auth", "http://buncheoleasy.com/login/app"})
    void 커스텀_스킴과_http_는_가로챌_수_있어_기동에서_막는다(final String value) {
      // 커스텀 스킴은 같은 스킴을 등록한 다른 앱이 시작 요청을 열면 코드가 그 앱으로 간다(RFC 8252 §8.6).
      assertThatThrownBy(() -> new AppLoginRedirector(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("https");
    }
  }

  @Nested
  @DisplayName("돌려보내기")
  class RedirectTest {

    @Test
    void 코드는_앱_주소의_쿼리로_싣는다() throws Exception {
      MockHttpServletResponse response = new MockHttpServletResponse();

      new AppLoginRedirector("https://buncheoleasy.com/login/app")
          .redirectWithCode(response, "abc", true);

      assertThat(response.getRedirectedUrl())
          .isEqualTo("https://buncheoleasy.com/login/app?code=abc&signup=1");
    }

    @Test
    void 꺼져_있으면_코드를_상대_주소로_싣지_않고_거절한다() {
      // 빈 주소로 조립하면 Location 이 "?code=…" 가 되어 이 서버 경로로 코드가 샌다.
      MockHttpServletResponse response = new MockHttpServletResponse();
      AppLoginRedirector redirector = new AppLoginRedirector("");

      assertThatThrownBy(() -> redirector.redirectWithCode(response, "abc", false))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_DISABLED);
      assertThatThrownBy(
              () -> redirector.redirectWithError(response, LoginFailureRedirector.Reason.FAILED))
          .isInstanceOf(BusinessException.class);
      assertThat(response.getRedirectedUrl()).isNull();
    }
  }
}
