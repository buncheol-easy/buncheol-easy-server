package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminMeQueryService;
import buncheoleasy.admin.domain.AdminRole;
import buncheoleasy.admin.dto.response.AdminMeResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AdminMeController 문서화 테스트")
class AdminMeControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private AdminMeQueryService adminMeQueryService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void 관리자_본인_확인() throws Exception {
    // given
    given(adminMeQueryService.getMe(anyLong()))
        .willReturn(new AdminMeResponse("buncheol-admin", AdminRole.ADMIN));

    // when & then
    mockMvc
        .perform(get("/v1/admin/me").header("Authorization", "Bearer {adminAccessToken}"))
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
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {adminAccessToken}"))
                        .responseSchema(Schema.schema("AdminMeResponse"))
                        .responseFields(
                            fieldWithPath("loginId").description("관리자 로그인 ID"),
                            fieldWithPath("role").description("관리자 권한 등급: ADMIN"))
                        .build())));
  }
}
