package buncheoleasy.auth.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AuthController 문서화 테스트")
class AuthControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private SocialLoginService socialLoginService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

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
  void 토큰_재발급() throws Exception {
    // given
    given(socialLoginService.reissueTokens("refresh-token-sample"))
        .willReturn(new TokenPair("new-access-token", "new-refresh-token"));

    // when & then
    mockMvc
        .perform(
            post("/v1/auth/reissue-token")
                .cookie(new Cookie("refreshToken", "refresh-token-sample")))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-reissue-token",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth")
                        .summary("토큰 재발급")
                        .description(
                            "요청 시 `refreshToken` 쿠키(HttpOnly)를 전달하면 새 Access Token을 발급한다. "
                                + "새 Refresh Token은 응답의 Set-Cookie 헤더로 갱신된다.")
                        .responseSchema(Schema.schema("AccessTokenResponse"))
                        .responseFields(fieldWithPath("accessToken").description("새 Access Token"))
                        .build())));
  }

  @Test
  void 로그아웃() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/v1/auth/logout")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "auth-logout",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth")
                        .summary("로그아웃")
                        .description("현재 사용자의 Refresh Token을 무효화하고 Refresh 쿠키를 만료시킨다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
