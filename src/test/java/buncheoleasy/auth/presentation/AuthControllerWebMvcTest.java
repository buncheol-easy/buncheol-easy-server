package buncheoleasy.auth.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
@DisplayName("AuthController WebMvc 테스트")
class AuthControllerWebMvcTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RequestPostProcessor mockAuth() {
        return (MockHttpServletRequest request) -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList())
            );
            return request;
        };
    }

    @Test
    void 토큰_재발급_요청이_성공하면_200을_반환한다() throws Exception {
        given(socialLoginService.reissueTokens("valid-refresh"))
                .willReturn(new TokenPair("new-access", "new-refresh"));

        mockMvc.perform(post("/v1/auth/reissue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void 토큰_재발급_요청_검증에_실패하면_400과_표준_에러코드를_반환한다() throws Exception {
        mockMvc.perform(post("/v1/auth/reissue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void 토큰_재발급_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
        given(socialLoginService.reissueTokens("invalid-refresh"))
                .willThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        mockMvc.perform(post("/v1/auth/reissue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-refresh\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ErrorCode.AUTH_INVALID_TOKEN.getCode())));
    }

    @Test
    void 로그아웃_요청이_성공하면_204를_반환한다() throws Exception {
        mockMvc.perform(post("/v1/auth/logout").with(mockAuth()))
                .andExpect(status().isNoContent());

        then(socialLoginService).should().logout(USER_ID);
    }
}
