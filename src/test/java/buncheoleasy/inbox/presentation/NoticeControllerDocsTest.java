package buncheoleasy.inbox.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.inbox.application.NoticeCommandService;
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
@DisplayName("NoticeController 문서화 테스트")
class NoticeControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private NoticeCommandService noticeCommandService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void 공지_작성() throws Exception {
    // given
    given(noticeCommandService.createNotice(any())).willReturn(1L);

    // when & then
    mockMvc
        .perform(
            post("/v1/notices")
                .header("Authorization", "Bearer {accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"[필독] 서비스 점검 안내","reference":"결제 일시 중단",\
                    "description":"6/20 02:00~04:00 서버 점검이 예정되어 있습니다.",\
                    "pinned":true,"linkPath":"/notice/2026-06-20"}"""))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "notices-create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .summary("공지 작성")
                        .description("공지를 등록한다. 인증된 사용자면 작성 가능(소유권/관리자 인가는 추후). 생성 시 Location 으로 상세 경로를 반환한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("CreateNoticeRequest"))
                        .requestFields(
                            fieldWithPath("title").description("제목(필수, 200자 이하)"),
                            fieldWithPath("reference")
                                .description("참고(보조 텍스트, 200자 이하)")
                                .optional(),
                            fieldWithPath("description").description("설명(본문, 필수)"),
                            fieldWithPath("pinned")
                                .description("상단 고정 여부(선택, 생략·null 시 false)")
                                .optional(),
                            fieldWithPath("linkPath")
                                .description("연관 화면 in-app 경로(500자 이하)")
                                .optional())
                        .build())));
  }

  @Test
  void 공지_상단_고정() throws Exception {
    // when & then
    mockMvc
        .perform(
            put("/v1/notices/{noticeId}/pin", 1L)
                .header("Authorization", "Bearer {accessToken}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "notices-pin",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .summary("공지 상단 고정")
                        .description("공지를 상단 고정한다. 공지가 아니면 409(INB-004). 멱등.")
                        .pathParameters(parameterWithName("noticeId").description("공지(메시지) ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 공지_상단_고정_해제() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/v1/notices/{noticeId}/pin", 1L)
                .header("Authorization", "Bearer {accessToken}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "notices-unpin",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .summary("공지 상단 고정 해제")
                        .description("공지의 상단 고정을 해제한다. 공지가 아니면 409(INB-004). 멱등.")
                        .pathParameters(parameterWithName("noticeId").description("공지(메시지) ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
