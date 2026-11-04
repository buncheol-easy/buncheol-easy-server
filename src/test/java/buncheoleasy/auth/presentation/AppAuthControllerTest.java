package buncheoleasy.auth.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.AppLoginResult;
import buncheoleasy.auth.application.AppLoginService;
import buncheoleasy.auth.infrastructure.jwt.JwtAuthenticationFilter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AppAuthController 테스트")
class AppAuthControllerTest {

  private static final Long USER_ID = 1L;
  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final AppLoginResult RESULT =
      new AppLoginResult(new TokenPair("app-access", "app-refresh"), true);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AppLoginService appLoginService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor auth(final String... authorities) {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  USER_ID,
                  null,
                  Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
      return request;
    };
  }

  @Nested
  @DisplayName("POST /v1/auth/app/token")
  class TokenTest {

    @Test
    void 코드를_바꾸면_두_토큰과_가입_여부를_본문으로_주고_쿠키는_굽지_않는다() throws Exception {
      given(appLoginService.exchangeCode("code", VERIFIER)).willReturn(RESULT);

      mockMvc
          .perform(
              post("/v1/auth/app/token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"code\":\"code\",\"verifier\":\"" + VERIFIER + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("app-access"))
          .andExpect(jsonPath("$.refreshToken").value("app-refresh"))
          .andExpect(jsonPath("$.signup").value(true))
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "{\"code\":\"code\",\"verifier\":\"too-short\"}",
          "{\"code\":\"code\",\"verifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjX!\"}",
          "{\"code\":\"\",\"verifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}",
          "{\"verifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}"
        })
    void 형식이_틀리면_서비스를_부르지_않고_400(final String body) throws Exception {
      mockMvc
          .perform(post("/v1/auth/app/token").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(appLoginService).should(never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void 코드가_틀리면_401_AUTH_007() throws Exception {
      given(appLoginService.exchangeCode("code", VERIFIER))
          .willThrow(new BusinessException(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID));

      mockMvc
          .perform(
              post("/v1/auth/app/token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"code\":\"code\",\"verifier\":\"" + VERIFIER + "\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().string(containsString("AUTH-007")));
    }
  }

  @Nested
  @DisplayName("POST /v1/auth/app/apple")
  class AppleTest {

    @Test
    void 애플_로그인은_두_토큰과_가입_여부를_준다() throws Exception {
      given(appLoginService.loginWithApple("identity", "nonce", "홍길동", "127.0.0.1"))
          .willReturn(RESULT);

      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"identityToken\":\"identity\",\"rawNonce\":\"nonce\",\"fullName\":\"홍길동\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("app-access"))
          .andExpect(jsonPath("$.refreshToken").value("app-refresh"))
          .andExpect(jsonPath("$.signup").value(true));
    }

    @Test
    void 이름은_없어도_된다() throws Exception {
      given(appLoginService.loginWithApple("identity", "nonce", null, "127.0.0.1"))
          .willReturn(RESULT);

      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"identity\",\"rawNonce\":\"nonce\"}"))
          .andExpect(status().isOk());
    }

    @Test
    void 실패_제한_키는_Nginx_가_덮어쓴_X_Real_IP_로_한다() throws Exception {
      given(appLoginService.loginWithApple("identity", "nonce", null, "203.0.113.9"))
          .willReturn(RESULT);

      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .header("X-Real-IP", "203.0.113.9")
                  .header("X-Forwarded-For", "1.1.1.1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"identity\",\"rawNonce\":\"nonce\"}"))
          .andExpect(status().isOk());
    }

    @Test
    void IP_실패_한도에_닿으면_429_AUTH_012() throws Exception {
      given(appLoginService.loginWithApple("identity", "nonce", null, "127.0.0.1"))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_LOGIN_RATE_LIMITED));

      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"identity\",\"rawNonce\":\"nonce\"}"))
          .andExpect(status().isTooManyRequests())
          .andExpect(content().string(containsString("AUTH-012")));
    }

    @Test
    void nonce_가_없으면_400() throws Exception {
      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"identity\"}"))
          .andExpect(status().isBadRequest());

      then(appLoginService).should(never()).loginWithApple(any(), any(), any(), any());
    }

    @Test
    void 허용_앱이_설정되지_않은_서버면_400_AUTH_010() throws Exception {
      given(appLoginService.loginWithApple("identity", "nonce", null, "127.0.0.1"))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_SIGNIN_DISABLED));

      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"identity\",\"rawNonce\":\"nonce\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString("AUTH-010")));
    }
  }

  @Nested
  @DisplayName("POST /v1/auth/app/reissue · /logout")
  class ReissueAndLogoutTest {

    @Test
    void 갱신은_두_토큰을_본문으로_준다() throws Exception {
      given(appLoginService.reissueTokens("app-refresh"))
          .willReturn(new TokenPair("new-access", "new-refresh"));

      mockMvc
          .perform(
              post("/v1/auth/app/reissue")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"app-refresh\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("new-access"))
          .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void 로그아웃은_앱_칸만_지우고_쿠키를_건드리지_않는다() throws Exception {
      mockMvc
          .perform(post("/v1/auth/app/logout").with(auth("ROLE_USER")))
          .andExpect(status().isNoContent())
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

      then(appLoginService).should().logout(USER_ID);
    }

    @Test
    void 관리자_재현_토큰으로는_앱_칸도_지우지_않는다() throws Exception {
      mockMvc
          .perform(
              post("/v1/auth/app/logout")
                  .with(auth("ROLE_USER", JwtAuthenticationFilter.IMPERSONATED_AUTHORITY)))
          .andExpect(status().isNoContent());

      then(appLoginService).should(never()).logout(any());
    }
  }

  @Test
  void 응답_필드는_세_개뿐이다() throws Exception {
    // 앱 파서와 맞물리는 모양 — 필드를 바꾸면 앱 배포 순서가 필요하다.
    given(appLoginService.exchangeCode("code", VERIFIER)).willReturn(RESULT);

    mockMvc
        .perform(
            post("/v1/auth/app/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code\",\"verifier\":\"" + VERIFIER + "\"}"))
        .andExpect(
            content()
                .json(
                    "{\"accessToken\":\"app-access\",\"refreshToken\":\"app-refresh\","
                        + "\"signup\":true}",
                    JsonCompareMode.STRICT));
  }
}
