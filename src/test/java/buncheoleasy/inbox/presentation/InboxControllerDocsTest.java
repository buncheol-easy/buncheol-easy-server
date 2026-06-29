package buncheoleasy.inbox.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.inbox.application.InboxQueryService;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.response.InboxMessageDetailResponse;
import buncheoleasy.inbox.dto.response.InboxMessageSummaryResponse;
import buncheoleasy.inbox.dto.response.InboxResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@DisplayName("InboxController 문서화 테스트")
class InboxControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private InboxQueryService inboxQueryService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void 수신함_목록_조회() throws Exception {
    // given
    InboxResponse response =
        new InboxResponse(
            List.of(
                new InboxMessageSummaryResponse(
                    10L,
                    "[필독] 서비스 점검 안내",
                    true,
                    InboxMessageType.NOTICE,
                    Instant.parse("2026-06-15T00:00:00Z"))),
            new CursorResponse<>(
                List.of(
                    new InboxMessageSummaryResponse(
                        9L,
                        "분철 낙찰 안내",
                        false,
                        InboxMessageType.NOTIFICATION,
                        Instant.parse("2026-06-14T09:00:00Z")),
                    new InboxMessageSummaryResponse(
                        8L,
                        "여름 이벤트 공지",
                        false,
                        InboxMessageType.NOTICE,
                        Instant.parse("2026-06-13T09:00:00Z"))),
                "2026-06-13T09:00:00Z_8",
                true));
    given(inboxQueryService.getInbox(any(), any(), any(), anyInt())).willReturn(response);

    // when & then
    mockMvc
        .perform(get("/v1/inbox").param("type", "NOTICE").param("size", "20"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "inbox-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Inbox")
                        .summary("수신함 목록 조회")
                        .description(
                            """
                            수신한 공지/알림 목록을 등록 최신순으로 조회한다. 비로그인도 호출 가능하며 이때는 공지만 보인다.
                            type 미지정 시 전체(공지 + 본인 알림), NOTICE/NOTIFICATION 으로 필터링한다.
                            상단 고정 공지는 첫 페이지 응답의 pinned 로 분리해 내려가고(2페이지부터 빈 배열),
                            본문 피드 feed 는 커서 기반 무한스크롤이다.""")
                        .queryParameters(
                            parameterWithName("type")
                                .description("필터: NOTICE(공지만) | NOTIFICATION(알림만), 미지정 시 전체")
                                .optional(),
                            parameterWithName("cursor")
                                .description("다음 페이지 커서(이전 응답의 feed.nextCursor). 첫 페이지는 생략")
                                .optional(),
                            parameterWithName("size")
                                .description("페이지 크기(기본 20, 1~50로 보정)")
                                .optional())
                        .responseSchema(Schema.schema("InboxResponse"))
                        .responseFields(
                            fieldWithPath("pinned[].id").description("메시지 ID"),
                            fieldWithPath("pinned[].title").description("제목"),
                            fieldWithPath("pinned[].pinned").description("상단 고정 여부(고정 영역은 항상 true)"),
                            fieldWithPath("pinned[].type").description("종류: NOTICE | NOTIFICATION"),
                            fieldWithPath("pinned[].createdAt").description("등록 일시(UTC)"),
                            fieldWithPath("feed.items[].id").description("메시지 ID"),
                            fieldWithPath("feed.items[].title").description("제목"),
                            fieldWithPath("feed.items[].pinned").description("상단 고정 여부"),
                            fieldWithPath("feed.items[].type").description("종류: NOTICE | NOTIFICATION"),
                            fieldWithPath("feed.items[].createdAt").description("등록 일시(UTC)"),
                            fieldWithPath("feed.nextCursor")
                                .description("다음 페이지 커서(없으면 null)")
                                .optional(),
                            fieldWithPath("feed.hasNext").description("다음 페이지 존재 여부"))
                        .build())));
  }

  @Test
  void 수신함_상세_조회() throws Exception {
    // given
    given(inboxQueryService.getInboxMessage(any(), any()))
        .willReturn(
            new InboxMessageDetailResponse(
                8L,
                "여름 이벤트 공지",
                "이벤트 안내",
                "7월 한 달간 진행되는 여름 이벤트 안내입니다.",
                InboxMessageType.NOTICE,
                Instant.parse("2026-06-13T09:00:00Z"),
                false,
                "/events/summer",
                "https://cdn.example.com/notices/8/images/summer.jpg"));

    // when & then
    mockMvc
        .perform(get("/v1/inbox/{messageId}", 8L))
        .andExpect(status().isOk())
        .andDo(
            document(
                "inbox-detail",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Inbox")
                        .summary("수신함 단건 상세 조회")
                        .description(
                            "공지이거나 본인 알림이면 상세를 반환한다. 그 외(타인 알림 등)는 404. 비로그인은 공지만 조회 가능.")
                        .pathParameters(parameterWithName("messageId").description("메시지 ID"))
                        .responseSchema(Schema.schema("InboxMessageDetailResponse"))
                        .responseFields(
                            fieldWithPath("id").description("메시지 ID"),
                            fieldWithPath("title").description("제목"),
                            fieldWithPath("reference").description("참고(보조 텍스트, 없으면 null)").optional(),
                            fieldWithPath("description").description("설명(본문)"),
                            fieldWithPath("type").description("종류: NOTICE | NOTIFICATION"),
                            fieldWithPath("createdAt").description("등록 일시(UTC)"),
                            fieldWithPath("pinned").description("상단 고정 여부"),
                            fieldWithPath("linkPath")
                                .description("연관 화면 in-app 경로(없으면 null)")
                                .optional(),
                            fieldWithPath("imageUrl")
                                .description("공지 본문 이미지 URL(없으면 null). 알림은 항상 null")
                                .optional())
                        .build())));
  }
}
