package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminMeQueryService;
import buncheoleasy.admin.dto.response.AdminMeResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminMeController 문서화 테스트")
class AdminMeControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminMeQueryService adminMeQueryService;

  @Test
  void 관리자_본인_확인() throws Exception {
    // given
    given(adminMeQueryService.getMe(anyLong())).willReturn(new AdminMeResponse("buncheol-admin"));

    // when & then
    mockMvc
        .perform(get("/v1/admin/me").with(adminAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-me",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 본인 확인")
                        .description(
                            """
                            현재 로그인한 관리자 정보 조회 (ROLE_ADMIN 전용). admin 프론트가 저장된 토큰의 세션 유효성을
                            확인할 때 쓴다 — 200 이면 대시보드 진입, 401/403 이면 로그인 화면으로. 계정이 삭제됐으면 403(ADM-001).""")
                        .requestHeaders(adminAuthorizationHeader())
                        .responseSchema(Schema.schema("AdminMeResponse"))
                        .responseFields(fieldWithPath("loginId").description("관리자 로그인 ID"))
                        .build())));
  }
}
