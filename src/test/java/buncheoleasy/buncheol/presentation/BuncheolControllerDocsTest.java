package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("BuncheolController 문서화 테스트")
class BuncheolControllerDocsTest {

  private static final Long HOST_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;

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
              new UsernamePasswordAuthenticationToken(HOST_ID, null, Collections.emptyList()));
      return request;
    };
  }

  private String holdRequestJson() {
    return """
        {
          "groupId": 100,
          "title": "뉴진스 1집 분철",
          "description": "공식 스토어 단독 구성",
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          "gs25ShippingFee": 3000,
          "cuShippingFee": null,
          "buncheolMembers": [
            {"memberId": 200, "bidMinPrice": 50000}
          ]
        }
        """
        .formatted(Instant.now().plus(7, ChronoUnit.DAYS));
  }

  private String modifyRequestJson() {
    return """
        {
          "title": "뉴진스 1집 분철 (수정)",
          "description": "공식 스토어 단독 구성",
          "keepImageIds": [1, 2]
        }
        """;
  }

  @Test
  void 분철_개최() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, holdRequestJson().getBytes());

    mockMvc
        .perform(
            multipart("/v1/buncheols")
                .file(requestPart)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "buncheols-hold",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 개최")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .description(
                            """
                            multipart/form-data 요청.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "groupId": Long,                // 그룹 ID
                              "title": String,                // 1~200자
                              "description": String?,         // 선택, 300자 이하
                              "purchaseSite": String,         // 1~200자
                              "deadline": Instant,            // 미래 시점 (UTC ISO-8601, 예: 2026-06-01T03:00:00Z)
                              "gs25ShippingFee": Integer?,    // 양수, gs25/cu 중 최소 1개 필수
                              "cuShippingFee": Integer?,      // 양수, gs25/cu 중 최소 1개 필수
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "bidMinPrice": Long         // 양수
                                }
                              ]
                            }
                            ```

                            **images 파트** (선택): 이미지 파일 목록, **최대 5장**
                            """)
                        .build())));
  }

  @Test
  void 분철_수정() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, modifyRequestJson().getBytes());

    MockMultipartHttpServletRequestBuilder builder =
        multipart("/v1/buncheols/{id}", 10L)
            .file(requestPart)
            .header("Authorization", "Bearer {accessToken}")
            .with(
                request -> {
                  request.setMethod("PUT");
                  return request;
                })
            .with(mockAuth());

    mockMvc
        .perform(builder)
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-modify",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 수정")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .description(
                            """
                            multipart/form-data PUT.

                            모집중(RECRUITING)이고 마감 전인 분철만 수정 가능하며, 제목·설명·이미지만 변경할 수 있다.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "title": String,                // 1~200자
                              "description": String?,         // 선택, 300자 이하
                              "keepImageIds": [Long]          // 유지할 기존 이미지 ID (비어있으면 모두 제거)
                            }
                            ```

                            **images 파트** (선택): 새로 업로드할 이미지 파일 목록.
                            `keepImageIds.size + images.size` 가 **최대 5장** 이어야 함
                            """)
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));
  }

  @Test
  void 내_개최_분철_목록_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
    MyHostedBuncheolResponse response =
        new MyHostedBuncheolResponse(
            10L, "뉴진스 1집 분철", "뉴진스", BuncheolStatus.RECRUITING, deadline, 5, 7L, createdAt);
    given(myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID)).willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/buncheols/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-list-my-hosted",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("내가 개최한 분철 목록 조회")
                        .description("마이페이지에서 호스트 본인이 개최한 분철 목록을 최신 개최 순으로 조회한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("MyHostedBuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].title").description("분철 제목"),
                            fieldWithPath("[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("[].status")
                                .description("분철 진행 상태 (RECRUITING | CLOSED | ...)"),
                            fieldWithPath("[].deadline").description("분철 모집 마감일"),
                            fieldWithPath("[].memberSlotCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].activeParticipationCount")
                                .description("활성 참여자 수 (ACTIVE_BID/AWAITING_PAYMENT/CONFIRMED)"),
                            fieldWithPath("[].createdAt").description("분철 개최 일시"))
                        .build())));
  }

  @Test
  void 분철_목록_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    BuncheolSummaryResponse item =
        new BuncheolSummaryResponse(10L, "뉴진스 1집 분철", deadline, true, "뉴진스", List.of("민지", "혜인"));
    CursorResponse<BuncheolSummaryResponse> response =
        new CursorResponse<>(List.of(item), "2026-05-15T08:00:00Z_10", true);

    given(
            buncheolListQueryService.search(
                HOST_ID,
                new BuncheolSearchCondition(100L, 200L, "뉴진스"),
                Cursor.parse("2026-05-15T08:00:00Z_15"),
                20))
        .willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols")
                .queryParam("groupId", "100")
                .queryParam("memberId", "200")
                .queryParam("keyword", "뉴진스")
                .queryParam("cursor", "2026-05-15T08:00:00Z_15")
                .queryParam("size", "20")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 목록 조회 (공개 탐색)")
                        .description(
                            """
                            공개 분철 카드 리스트를 그룹/멤버/키워드 필터로 검색한다. **비로그인 호출 허용** — 토큰 없이도 호출 가능하며 이 경우
                            모든 항목의 `bookmarked` 가 `false`. 토큰을 주면 본인 찜 여부가 채워진다.

                            **응답 동작**
                            - 정렬: `createdAt DESC, id DESC` 고정
                            - 노출 상태: `CANCELLED` 를 제외한 모든 status (`RECRUITING` / `CLOSED` / `PAID` / `SETTLING` / `FINISHED`)
                            - 페이지: **커서 기반 무한스크롤**. 응답의 `nextCursor` 를 다음 요청의 `cursor` 로 그대로 전달
                            - `nextCursor` 형식: `<createdAt Instant ISO-8601>_<id>` (예: `2026-05-15T08:00:00Z_10`)
                            - `hasNext=false` 면 `nextCursor` 는 `null`

                            **쿼리 파라미터 (모두 선택)**
                            - `groupId`: 그룹 ID 정확 일치
                            - `memberId`: 단일 멤버 ID — 해당 멤버가 포함된 분철만
                            - `keyword`: title 또는 description 부분 일치 (대소문자 무관, 최대 100자)
                            - `cursor`: 다음 페이지 커서. 미지정 시 첫 페이지
                            - `size`: 페이지 크기 (기본 20, 서버에서 1~50 으로 클램프)

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `keyword` 가 100자 초과 |
                            | 400 | `PAGE-001` (`CURSOR_INVALID`) | 커서 형식 오류 (구분자/Instant/숫자 파싱 실패) |
                            """)
                        .requestHeaders(
                            headerWithName("Authorization")
                                .description("`Bearer {accessToken}` — 비로그인 호출이면 헤더 자체를 생략")
                                .optional())
                        .queryParameters(
                            parameterWithName("groupId").description("그룹 ID 필터").optional(),
                            parameterWithName("memberId").description("단일 멤버 ID 필터").optional(),
                            parameterWithName("keyword")
                                .description("title/description 부분 일치 키워드")
                                .optional(),
                            parameterWithName("cursor")
                                .description("`<createdAt>_<id>` 형식, 첫 페이지는 생략")
                                .optional(),
                            parameterWithName("size")
                                .description("페이지 크기 (기본 20, 1~50)")
                                .optional())
                        .responseSchema(Schema.schema("BuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("items").description("분철 카드 배열"),
                            fieldWithPath("items[].id").description("분철 ID"),
                            fieldWithPath("items[].title").description("분철 제목"),
                            fieldWithPath("items[].deadline")
                                .description("분철 모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("items[].bookmarked")
                                .description("호출 사용자의 본인 찜 여부 (비로그인이면 항상 false)"),
                            fieldWithPath("items[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("items[].memberNames")
                                .description("분철에 포함된 멤버 이름 (호스트 등록 슬롯 순)"),
                            fieldWithPath("nextCursor")
                                .description(
                                    "다음 페이지 커서 — `<createdAt>_<id>`. `hasNext=false` 면 null")
                                .optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"))
                        .build())));
  }

  @Test
  void 분철_목록_조회_keyword_가_100자_초과면_400과_INVALID_INPUT_VALUE를_반환한다() throws Exception {
    String overLimit = "a".repeat(101);

    mockMvc
        .perform(get("/v1/buncheols").queryParam("keyword", overLimit))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C-001"));

    verifyNoInteractions(buncheolListQueryService);
  }

  @Test
  void 분철_목록_조회_비로그인_호출은_userId_null_로_전달되고_bookmarked_가_false_로_내려간다() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    BuncheolSummaryResponse item =
        new BuncheolSummaryResponse(10L, "뉴진스 1집 분철", deadline, false, "뉴진스", List.of("민지"));
    CursorResponse<BuncheolSummaryResponse> response =
        new CursorResponse<>(List.of(item), null, false);

    given(
            buncheolListQueryService.search(
                null, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 20))
        .willReturn(response);

    mockMvc
        .perform(get("/v1/buncheols"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].bookmarked").value(false))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void 분철_취소() throws Exception {
    mockMvc
        .perform(
            delete("/v1/buncheols/{id}", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 취소")
                        .description("호스트가 자신이 개최한 분철을 취소한다.")
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
