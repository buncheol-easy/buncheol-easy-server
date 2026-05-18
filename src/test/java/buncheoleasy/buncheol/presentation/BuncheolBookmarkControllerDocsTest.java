package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolBookmarkService;
import buncheoleasy.buncheol.application.MyBookmarkedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BookmarkSortOption;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
@DisplayName("BuncheolBookmarkController 문서화 테스트")
class BuncheolBookmarkControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BuncheolBookmarkService buncheolBookmarkService;
  @MockitoBean private MyBookmarkedBuncheolQueryService myBookmarkedBuncheolQueryService;
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
  void 분철_찜_등록() throws Exception {
    mockMvc
        .perform(
            post("/v1/buncheols/{buncheolId}/bookmark", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "buncheols-bookmark-add",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BuncheolBookmark")
                        .summary("분철 찜 등록")
                        .description(
                            "분철을 사용자의 찜 목록에 추가한다. 분철 상태(모집중/마감/취소 등)에 관계없이 가능. 이미 찜한 분철이면 409 반환.")
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 분철_찜_해제() throws Exception {
    mockMvc
        .perform(
            delete("/v1/buncheols/{buncheolId}/bookmark", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-bookmark-remove",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BuncheolBookmark")
                        .summary("분철 찜 해제")
                        .description("사용자가 찜한 분철을 찜 목록에서 제거한다. 찜이 없으면 404 반환.")
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 내_찜한_분철_목록_조회() throws Exception {
    MyBookmarkedBuncheolResponse response =
        new MyBookmarkedBuncheolResponse(
            500L,
            10L,
            "뉴진스 1집 분철",
            BuncheolStatus.RECRUITING,
            Instant.parse("2026-06-01T12:00:00Z"),
            "뉴진스",
            "https://cdn.example.com/buncheol-thumb.jpg");
    given(
            myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
                USER_ID, BookmarkSortOption.LATEST, false, false))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/buncheols/bookmarks/me")
                .param("sort", "LATEST")
                .param("hideClosed", "false")
                .param("onlyFavoriteGroups", "false")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-bookmark-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BuncheolBookmark")
                        .summary("내가 찜한 분철 목록 조회")
                        .description(
                            """
                            마이페이지에서 사용자가 찜한 분철 카드 리스트를 조회한다.

                            **쿼리 파라미터** (모두 선택)
                            - `sort`: `LATEST` (기본, 찜 등록 시각 내림차순) | `DEADLINE` (분철 마감 임박순)
                            - `hideClosed`: `false` (기본) | `true` — true 면 모집중(`RECRUITING`)이 아닌 분철은 결과에서 제외
                            - `onlyFavoriteGroups`: `false` (기본) | `true` — true 면 분철의 그룹이 사용자 최애 그룹 중 하나인 것만 포함
                            """)
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .queryParameters(
                            parameterWithName("sort")
                                .description("정렬 기준 — `LATEST` | `DEADLINE`. 기본값 `LATEST`")
                                .optional(),
                            parameterWithName("hideClosed")
                                .description("모집중이 아닌 분철 숨김 여부 (`true` | `false`). 기본값 `false`")
                                .optional(),
                            parameterWithName("onlyFavoriteGroups")
                                .description(
                                    "최애 그룹 분철만 보기 (`true` | `false`). 기본값 `false`")
                                .optional())
                        .responseSchema(Schema.schema("MyBookmarkedBuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("[].bookmarkId").description("찜 ID"),
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].title").description("분철 제목"),
                            fieldWithPath("[].status")
                                .description("분철 진행 상태 (RECRUITING | CLOSED | ...)"),
                            fieldWithPath("[].deadline").description("분철 모집 마감일"),
                            fieldWithPath("[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("[].thumbnailUrl")
                                .description("분철 대표 이미지 URL. 이미지가 없으면 null")
                                .optional())
                        .build())));
  }
}
