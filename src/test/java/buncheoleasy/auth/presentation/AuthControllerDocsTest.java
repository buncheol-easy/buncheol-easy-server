package buncheoleasy.auth.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AuthController 문서화 테스트")
class AuthControllerDocsTest extends DocsTestSupport {

  @MockitoBean private SocialLoginService socialLoginService;

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
                            "새 Access Token을 발급한다. **필수 입력: `refreshToken` 쿠키(HttpOnly)** — "
                                + "Authorization 헤더가 아니라 쿠키로 전달해야 한다. "
                                + "새 Refresh Token은 응답의 Set-Cookie 헤더로 갱신된다.")
                        .responseSchema(Schema.schema("AccessTokenResponse"))
                        .responseFields(fieldWithPath("accessToken").description("새 Access Token"))
                        .build())));
  }

  @Test
  void 로그아웃() throws Exception {
    // when & then
    mockMvc
        .perform(post("/v1/auth/logout").with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "auth-logout",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth")
                        .summary("로그아웃")
                        .description("현재 사용자의 Refresh Token을 무효화하고 Refresh 쿠키를 만료시킨다.")
                        .requestHeaders(userAuthorizationHeader())
                        .build())));
  }
}
