package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminAuthService;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminAuthController 문서화 테스트")
class AdminAuthControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminAuthService adminAuthService;

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
                            refresh token 은 없으며 만료되면 다시 로그인한다. 아이디 없음/비밀번호 불일치는 같은 401(ADM-002)로 응답한다.

                            무차별 대입 방지를 위해 로그인 ID 축에 호출 제한을 둔다(기본 10분 5회).
                            한도를 넘기면 자격증명 검증 없이 429(ADM-003)로 응답하며, 로그인에 성공하면 누적이 초기화된다.""")
                        .requestSchema(Schema.schema("AdminLoginRequest"))
                        .requestFields(
                            fieldWithPath("loginId").description("관리자 로그인 ID (최대 50자)"),
                            fieldWithPath("password").description("비밀번호 (최대 72자)"))
                        .responseSchema(Schema.schema("AdminLoginResponse"))
                        .responseFields(
                            fieldWithPath("accessToken").description("관리자 Access Token"))
                        .build())));
  }

  @Test
  void 로그인_ID_에_ASCII_밖_문자가_있으면_400_으로_거부한다() throws Exception {
    // utf8mb4_unicode_ci 는 악센트를 무시해 buncheol-ádmin 이 buncheol-admin 계정을 찾는다.
    // 입력 단계에서 막지 않으면 변형마다 별도 호출 제한 카운터를 얻어 ID 축 한도가 무력화된다.
    mockMvc
        .perform(
            post("/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\": \"buncheol-\\u00e1dmin\", \"password\": \"password1234\"}"))
        .andExpect(status().isBadRequest());

    then(adminAuthService).shouldHaveNoInteractions();
  }

  @Test
  void 로그인_ID_에_개행이_있으면_400_으로_거부한다() throws Exception {
    // 감사 로그(log.warn "관리자 로그인 실패. loginId={}")에 임의 라인을 주입하는 것을 막는다.
    mockMvc
        .perform(
            post("/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\": \"admin\\nFAKE LOG LINE\", \"password\": \"password1234\"}"))
        .andExpect(status().isBadRequest());

    then(adminAuthService).shouldHaveNoInteractions();
  }
}
