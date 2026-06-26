package buncheoleasy.inbox.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.inbox.application.NoticeCommandService;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

  private String noticeRequestJson() {
    return """
        {
          "title": "[필독] 서비스 점검 안내",
          "reference": "결제 일시 중단",
          "description": "6/20 02:00~04:00 서버 점검이 예정되어 있습니다.",
          "pinned": true,
          "linkPath": "/notice/2026-06-20",
          "banner": {"title": "여름 이벤트"}
        }
        """;
  }

  @Test
  void 공지_작성() throws Exception {
    given(noticeCommandService.createNotice(any(), any(), any())).willReturn(1L);

    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, noticeRequestJson().getBytes());
    MockMultipartFile imagePart =
        new MockMultipartFile(
            "image", "notice-body.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    MockMultipartFile bannerImagePart =
        new MockMultipartFile(
            "bannerImage", "banner.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {4, 5, 6});

    mockMvc
        .perform(
            multipart("/v1/notices")
                .file(requestPart)
                .file(imagePart)
                .file(bannerImagePart)
                .header("Authorization", "Bearer {accessToken}"))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "notices-create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .summary("공지 작성")
                        .description(
                            """
                            multipart/form-data 요청. 인증된 사용자면 작성 가능(소유권/관리자 인가는 추후).
                            생성 시 Location 으로 상세 경로(`/v1/inbox/{id}`)를 반환한다.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "title": String,        // 제목(필수, 200자 이하)
                              "reference": String?,   // 참고(보조 텍스트, 200자 이하)
                              "description": String,  // 설명(본문, 필수, 5000자 이하)
                              "pinned": boolean,      // 상단 고정 여부
                              "linkPath": String?,    // 연관 화면 in-app 경로(/로 시작, 500자 이하)
                              "banner": {             // 홈 배너(선택). 넣으면 bannerImage 파트도 함께 필요
                                "title": String       // 배너 제목(필수, 200자 이하)
                              }
                            }
                            ```

                            **image 파트** (선택): 공지 본문 이미지 **최대 1장**. 커밋 후 비동기로 S3 업로드되어 상세 조회의 `imageUrl` 로 노출.

                            **bannerImage 파트** (선택): 홈 배너 이미지. `banner` 와 **함께** 와야 하며, 한쪽만 오면 `400 INB-006`.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `request` 검증 실패(제목/본문 누락 등) |
                            | 400 | `INB-006` (`NOTICE_BANNER_INCOMPLETE`) | 배너 제목/이미지 중 한쪽만 입력 |
                            | 400 | `FILE-002` (`FILE_EXTENSION_INVALID`) | jpg/jpeg/png/webp 외 확장자 (비동기 업로드 단계) |
                            """)
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 공지_상단_고정() throws Exception {
    mockMvc
        .perform(
            put("/v1/notices/{noticeId}/pin", 1L).header("Authorization", "Bearer {accessToken}"))
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
