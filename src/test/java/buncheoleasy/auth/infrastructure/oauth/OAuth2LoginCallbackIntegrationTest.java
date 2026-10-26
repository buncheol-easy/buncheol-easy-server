package buncheoleasy.auth.infrastructure.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * 실제 SecurityFilterChain 을 켠 채로 카카오 로그인 콜백 실패가 어디로 가는지 확인한다. 실패 핸들러 등록이 빠지거나 다른 경로로 새면 브라우저에 오류
 * JSON 이 그대로 보이므로, 핸들러 단위 테스트와 별도로 필터 체인 경계에서 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("카카오 로그인 콜백 실패 통합 테스트")
class OAuth2LoginCallbackIntegrationTest {

  private static final String CALLBACK = "/login/oauth2/code/kakao";

  @Autowired private MockMvc mockMvc;

  @Value("${app.frontend.login-callback-url}")
  private String loginCallbackUrl;

  @Value("${app.frontend.age-range-consent-callback-url}")
  private String ageRangeConsentCallbackUrl;

  @Test
  void 실제_인가_요청을_취소하면_취소됨으로_로그인_콜백에_돌려보낸다() throws Exception {
    // given: 카카오로 보내기 전 세션에 저장된 인가 요청과 그 state
    MvcResult authorization =
        mockMvc
            .perform(get("/oauth2/authorization/kakao"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
    String state =
        UriUtils.decode(
            UriComponentsBuilder.fromUriString(authorization.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("state"),
            StandardCharsets.UTF_8);

    // when & then: 카카오 「취소」가 돌려보내는 모양 그대로
    mockMvc
        .perform(
            get(CALLBACK)
                .session(session)
                .param("error", "access_denied")
                .param("error_description", "User denied access")
                .param("state", state))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(loginCallbackUrl + "?error=cancelled"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(content().string(""));
  }

  @Test
  void 저장된_인가_요청이_없으면_실패로_로그인_콜백에_돌려보낸다() throws Exception {
    mockMvc
        .perform(get(CALLBACK).param("error", "access_denied").param("state", "abc"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(loginCallbackUrl + "?error=failed"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(content().string(""));

    mockMvc
        .perform(get(CALLBACK).param("code", "x").param("state", "abc"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(loginCallbackUrl + "?error=failed"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void 콜백_파라미터가_없어도_실패로_로그인_콜백에_돌려보낸다() throws Exception {
    mockMvc
        .perform(get(CALLBACK))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(loginCallbackUrl + "?error=failed"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(content().string(""));
  }

  @Test
  void 연령대_추가_동의_콜백은_그대로_개최_화면으로_돌려보낸다() throws Exception {
    mockMvc
        .perform(
            get(CALLBACK)
                .param("error", "access_denied")
                .param(
                    "state",
                    KakaoAuthorizationRequestResolver.AGE_RANGE_CONSENT_STATE_PREFIX + "abc"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(ageRangeConsentCallbackUrl + "?ageRangeConsent=failed"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }
}
