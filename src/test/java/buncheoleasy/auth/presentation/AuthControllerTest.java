package buncheoleasy.auth.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AuthController 테스트")
class AuthControllerTest {

  private static final Long USER_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SocialLoginService socialLoginService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor mockAuth() {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Test
  void 토큰_재발급_요청이_성공하면_액세스토큰은_바디로_리프레시토큰은_쿠키로_응답한다() throws Exception {
    given(socialLoginService.reissueTokens("valid-refresh"))
        .willReturn(new TokenPair("new-access", "new-refresh"));

    mockMvc
        .perform(post("/v1/auth/reissue-token").cookie(new Cookie("refreshToken", "valid-refresh")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/v1/auth")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
  }

  @Test
  void 토큰_재발급_요청에_리프레시_쿠키가_없으면_400을_반환한다() throws Exception {
    mockMvc.perform(post("/v1/auth/reissue-token")).andExpect(status().isBadRequest());
  }

  @Test
  void 토큰_재발급_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
    given(socialLoginService.reissueTokens("invalid-refresh"))
        .willThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

    mockMvc
        .perform(
            post("/v1/auth/reissue-token").cookie(new Cookie("refreshToken", "invalid-refresh")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(containsString(ErrorCode.AUTH_INVALID_TOKEN.getCode())));
  }

  @Test
  void 로그아웃_요청이_성공하면_204를_반환하고_리프레시_쿠키를_만료시킨다() throws Exception {
    mockMvc
        .perform(post("/v1/auth/logout").with(mockAuth()))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    then(socialLoginService).should().logout(USER_ID);
  }
}
