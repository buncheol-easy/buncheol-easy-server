package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminImpersonationService;
import buncheoleasy.admin.dto.response.AdminImpersonationTokenResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminImpersonationController 문서화 테스트")
class AdminImpersonationControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminImpersonationService adminImpersonationService;

  @Test
  void 유저_impersonation_토큰_발급() throws Exception {
    // given
    given(adminImpersonationService.issueToken(anyLong(), anyLong(), anyString()))
        .willReturn(new AdminImpersonationTokenResponse(21L, "impersonation-access-token", 900L));

    // when & then
    mockMvc
        .perform(
            post("/v1/admin/users/{userId}/impersonation-token", 21L)
                .with(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"결제 상태 미반영 문의 재현\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-impersonation-token",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("유저 impersonation 토큰 발급")
                        .description(
                            """
                            관리자가 문의 재현용으로 대상 유저의 짧은 수명 access token(ROLE_USER)을 발급받는다 (ROLE_ADMIN 전용).
                            발급 토큰은 일반 유저 토큰과 동일해 유저 API 를 그대로 호출할 수 있으므로 조회 위주로 사용한다 — 쓰기 액션은 실제로 반영된다.
                            누가·언제·누구를·왜 접속했는지 감사 로그로 남기며, 사유(reason)는 필수다. 존재하지 않는 유저면 404(USR-016).
                            유저 본인 세션(refresh)은 건드리지 않아 강제 로그아웃되지 않는다.""")
                        .requestHeaders(adminAuthorizationHeader())
                        .requestSchema(Schema.schema("AdminImpersonationTokenRequest"))
                        .requestFields(
                            fieldWithPath("reason").description("발급 사유 — 감사 로그에 남는다 (최대 200자)"))
                        .responseSchema(Schema.schema("AdminImpersonationTokenResponse"))
                        .responseFields(
                            fieldWithPath("targetUserId").description("대상 유저 ID"),
                            fieldWithPath("accessToken").description("대상 유저의 Access Token (ROLE_USER)"),
                            fieldWithPath("expiresInSeconds").description("토큰 만료까지 남은 초 (기본 900 = 15분)"))
                        .build())));
  }
}
