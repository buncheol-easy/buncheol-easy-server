package buncheoleasy.auth.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 앱 주소(APP_LOGIN_CALLBACK_URL)가 비어 있는 서버 — 운영 기본값. 커스텀 스킴은 다른 앱이 가로챌 수 있어(RFC 8252 §8.6) 비면 앱 카카오
 * 로그인을 아예 시작하지 않는다. 실제 SecurityFilterChain 을 켠 채로 본다.
 */
@SpringBootTest(properties = "app.mobile.login-callback-url=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("앱 로그인 꺼짐 통합 테스트")
class AppLoginDisabledIntegrationTest {

  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

  @Autowired private MockMvc mockMvc;

  @Value("${app.frontend.login-callback-url}")
  private String webLoginCallbackUrl;

  @Test
  void 올바른_challenge_여도_카카오로_보내지_않고_400_AUTH_013() throws Exception {
    mockMvc
        .perform(get("/oauth2/authorization/kakao/app").param("challenge", CHALLENGE))
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().string(containsString("AUTH-013")));
  }

  @Test
  void 앱_state_로_온_실패_콜백은_웹_로그인_콜백으로_보낸다() throws Exception {
    mockMvc
        .perform(
            get("/login/oauth2/code/kakao")
                .param("error", "access_denied")
                .param("state", "app." + CHALLENGE + ".random"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(webLoginCallbackUrl + "?error=failed"));
  }

  @Test
  void 일반_로그인은_그대로_카카오로_간다() throws Exception {
    mockMvc
        .perform(get("/oauth2/authorization/kakao"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string(HttpHeaders.LOCATION, containsString("kauth.kakao.com")));
  }
}
