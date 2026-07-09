package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminAuthService;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AdminAuthController 문서화 테스트")
class AdminAuthControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private AdminAuthService adminAuthService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void 관리자_로그인() throws Exception {
    // given
    given(adminAuthService.login(anyString(), anyString()))
        .willReturn(new AdminLoginResponse("admin-access-token"));

    // when & then
    mockMvc
        .perform(
            post("/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\": \"buncheol-admin\", \"password\": \"password1234\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-auth-login",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 로그인")
                        .description(
                            """
                            관리자 ID/PW 로그인. 성공 시 role claim(ADMIN)이 실린 관리자 access token(기본 12시간)을 발급한다.
                            refresh token 은 없으며 만료되면 다시 로그인한다. 아이디 없음/비밀번호 불일치는 같은 401(ADM-002)로 응답한다.""")
                        .requestSchema(Schema.schema("AdminLoginRequest"))
                        .requestFields(
                            fieldWithPath("loginId").description("관리자 로그인 ID"),
                            fieldWithPath("password").description("비밀번호"))
                        .responseSchema(Schema.schema("AdminLoginResponse"))
                        .responseFields(
                            fieldWithPath("accessToken").description("관리자 Access Token"))
                        .build())));
  }
}
