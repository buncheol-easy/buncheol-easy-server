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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementOptionResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberBidResponse;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.MyBidResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.buncheol.dto.response.WinnerDeliveryResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;

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
        new BuncheolSummaryResponse(
            10L,
            "뉴진스 1집 분철",
            deadline,
            true,
            "뉴진스",
            "https://cdn.example.com/buncheol-10-thumb.jpg",
            List.of("민지", "혜인"));
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
                            fieldWithPath("items[].thumbnailUrl")
                                .description("대표이미지 URL — 분철에 등록된 첫 이미지. 이미지 없으면 null")
                                .optional(),
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
        new BuncheolSummaryResponse(10L, "뉴진스 1집 분철", deadline, false, "뉴진스", null, List.of("민지"));
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
  void 분철_단건_상세_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    BuncheolDetailResponse response =
        new BuncheolDetailResponse(
            10L,
            "뉴진스 1집 분철",
            "뉴진스",
            "공식 스토어",
            deadline,
            "공식 스토어 단독 구성",
            BuncheolStatus.RECRUITING,
            List.of("https://cdn.example.com/img1.jpg"),
            List.of(
                new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000),
                new ShippingOptionResponse(ShippingMethod.CU_HALF, 4000)),
            List.of(
                new BuncheolMemberBidResponse(
                    101L,
                    1001L,
                    "민지",
                    "https://cdn.example.com/minji.png",
                    40_000L,
                    List.of(90_000L, 70_000L, 50_000L),
                    4),
                new BuncheolMemberBidResponse(
                    102L,
                    1002L,
                    "해린",
                    "https://cdn.example.com/haerin.png",
                    30_000L,
                    List.of(35_000L),
                    1)),
            true,
            new MyParticipationSummaryResponse(
                2,
                List.of(
                    new MyBidResponse(601L, 101L, 50_000L, 3),
                    new MyBidResponse(351L, 102L, 35_000L, 1))));
    given(buncheolDetailQueryService.getDetail(10L, HOST_ID)).willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols/{id}", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-detail",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 단건 상세 조회")
                        .description(
                            """
                            분철 상세 화면에 필요한 모든 정보를 단일 응답으로 제공한다. **비로그인 호출 허용** — 토큰 없이도
                            호출 가능하며 이 경우 `myParticipation` 이 `null` 로 내려간다.

                            **응답 동작**
                            - `CANCELLED` 상태 분철도 200 으로 응답하며 `status` 로 구분
                            - 멤버별 `bidMinPrice` 는 호스트가 설정한 해당 멤버 슬롯의 최소 제시 금액 (원, 양수)
                            - 멤버별 `topBidAmounts` 는 활성 입찰 금액 DESC 상위 3개 (활성 = ACTIVE_BID / AWAITING_PAYMENT / CONFIRMED)
                            - `activeParticipantCount` 는 해당 멤버 슬롯의 활성 참여자 수
                            - `hostedByMe` 는 호출 유저가 개최자인지 여부 (비로그인 호출이면 항상 false)
                            - 로그인 유저 한정: `myParticipation.bids[].rank` 는 해당 멤버 내 내 입찰 금액 순위 (1-base)
                            - `myParticipation.participatedMemberCount` 는 이 분철에서 내가 활성 참여 중인 distinct 멤버 슬롯 수
                            - `myParticipation.bids[].participationId` 는 입찰 철회 API 호출에 사용

                            **응답 예시**
                            ```json
                            {
                              "id": 10,
                              "title": "뉴진스 1집 분철",
                              "groupName": "뉴진스",
                              "purchaseSite": "공식 스토어",
                              "deadline": "2026-06-01T12:00:00Z",
                              "description": "공식 스토어 단독 구성",
                              "status": "RECRUITING",
                              "imageUrls": ["https://cdn.example.com/img1.jpg"],
                              "shippingOptions": [
                                {"method": "GS25_HALF", "fee": 3000},
                                {"method": "CU_HALF", "fee": 4000}
                              ],
                              "members": [
                                {
                                  "buncheolMemberId": 101,
                                  "memberId": 1001,
                                  "memberName": "민지",
                                  "memberImage": "https://cdn.example.com/minji.png",
                                  "bidMinPrice": 40000,
                                  "topBidAmounts": [90000, 70000, 50000],
                                  "activeParticipantCount": 4
                                },
                                {
                                  "buncheolMemberId": 102,
                                  "memberId": 1002,
                                  "memberName": "해린",
                                  "memberImage": "https://cdn.example.com/haerin.png",
                                  "bidMinPrice": 30000,
                                  "topBidAmounts": [35000],
                                  "activeParticipantCount": 1
                                }
                              ],
                              "hostedByMe": true,
                              "myParticipation": {
                                "participatedMemberCount": 2,
                                "bids": [
                                  {"participationId": 601, "buncheolMemberId": 101, "bidAmount": 50000, "rank": 3},
                                  {"participationId": 351, "buncheolMemberId": 102, "bidAmount": 35000, "rank": 1}
                                ]
                              }
                            }
                            ```

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            """)
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization")
                                .description("`Bearer {accessToken}` — 비로그인 호출이면 헤더 자체를 생략")
                                .optional())
                        .responseSchema(Schema.schema("BuncheolDetailResponse"))
                        .responseFields(
                            fieldWithPath("id").description("분철 ID"),
                            fieldWithPath("title").description("분철 제목"),
                            fieldWithPath("groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("purchaseSite").description("구매처"),
                            fieldWithPath("deadline").description("모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("description").description("분철 설명").optional(),
                            fieldWithPath("status")
                                .description("분철 진행 상태 (RECRUITING / CLOSED / CANCELLED / ...)"),
                            fieldWithPath("imageUrls").description("분철 이미지 URL 배열 (등록 순)"),
                            fieldWithPath("shippingOptions").description("지원 배송방법 + 배송비 배열"),
                            fieldWithPath("shippingOptions[].method")
                                .description("배송방법 (GS25_HALF | CU_HALF)"),
                            fieldWithPath("shippingOptions[].fee").description("해당 배송방법 배송비 (원)"),
                            fieldWithPath("members").description("분철 멤버 슬롯 배열 (등록 순)"),
                            fieldWithPath("members[].buncheolMemberId")
                                .description("분철 멤버 슬롯 ID (참여 시 사용)"),
                            fieldWithPath("members[].memberId").description("그룹 멤버 ID"),
                            fieldWithPath("members[].memberName").description("멤버 이름"),
                            fieldWithPath("members[].memberImage")
                                .description("멤버 이미지 URL")
                                .optional(),
                            fieldWithPath("members[].bidMinPrice")
                                .description("호스트가 설정한 해당 멤버 슬롯의 최소 제시 금액 (원)"),
                            fieldWithPath("members[].topBidAmounts")
                                .description("실시간 활성 입찰 금액 DESC 상위 3개"),
                            fieldWithPath("members[].activeParticipantCount")
                                .description("해당 멤버 슬롯의 현재 활성 참여자 수"),
                            fieldWithPath("hostedByMe")
                                .description("호출 유저가 개최자인지 여부 (비로그인은 false)"),
                            fieldWithPath("myParticipation")
                                .description("로그인 유저의 활성 참여 요약. 비로그인이면 null")
                                .optional(),
                            fieldWithPath("myParticipation.participatedMemberCount")
                                .description("이 분철에서 내가 활성 참여 중인 distinct 멤버 슬롯 수")
                                .optional(),
                            fieldWithPath("myParticipation.bids")
                                .description("내 활성 입찰 목록 (멤버별 1건)")
                                .optional(),
                            fieldWithPath("myParticipation.bids[].participationId")
                                .description("참여 ID (입찰 철회 API에 사용)")
                                .optional(),
                            fieldWithPath("myParticipation.bids[].buncheolMemberId")
                                .description("내가 입찰한 멤버 슬롯 ID")
                                .optional(),
                            fieldWithPath("myParticipation.bids[].bidAmount")
                                .description("내 입찰 금액")
                                .optional(),
                            fieldWithPath("myParticipation.bids[].rank")
                                .description("해당 멤버 내 내 입찰 순위 (1-base)")
                                .optional())
                        .build())));
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

  @Test
  void 개최자_분철_관리_화면_조회() throws Exception {
    Instant deadline = Instant.parse("2026-05-27T00:00:00Z");
    WinnerDeliveryResponse winner =
        new WinnerDeliveryResponse(
            5001L,
            ShippingMethod.GS25_HALF,
            "GS25 강남역점",
            "유진팬",
            "010-1234-5678",
            "1234567890",
            DeliveryStatus.SHIPPING);
    BuncheolManagementResponse response =
        new BuncheolManagementResponse(
            10L,
            "호두 자랑",
            "IVE",
            "호두네",
            BuncheolStatus.CLOSED,
            deadline,
            4,
            1,
            List.of(
                new BuncheolManagementOptionResponse(
                    101L, 1001L, "안유진", "https://cdn.example.com/yujin.png", 1, 90_000L, winner),
                new BuncheolManagementOptionResponse(
                    102L, 1002L, "레이", "https://cdn.example.com/rei.png", 0, null, null)));
    given(buncheolManagementQueryService.getManagement(10L, HOST_ID)).willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols/{id}/management", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-management",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("개최자 분철 관리 화면 조회")
                        .description(
                            """
                            호스트 본인의 분철 관리 화면에 필요한 모든 정보를 단일 응답으로 제공한다.
                            **호스트 본인만 호출 가능** — 그 외 호출은 403.

                            **응답 동작**
                            - 분철 상태(`RECRUITING` / `CLOSED` / `PAID` / `SETTLING` / `FINISHED`) 무관하게 호출 가능
                            - `optionCount` = 분철에 등록된 멤버 슬롯 수
                            - `totalParticipationCount` = 분철 전체 참여 수 (ACTIVE_BID + AWAITING_PAYMENT + CONFIRMED). 한 유저가 여러 슬롯에 입찰할 수 있어 distinct 참여자 수와 다를 수 있음
                            - 옵션별 `participationCount` = 해당 옵션의 참여 수 (한 슬롯에 한 유저 활성 참여 최대 1건이라 사실상 참여자 수)
                            - 옵션별 `currentHighestBid` = 해당 옵션의 최고 제시 금액 (상태 무관). 참여 없으면 null. 마감 후 미낙찰 활성 입찰가가 낙찰가보다 높으면 `winner` 금액과 다를 수 있음
                            - 옵션별 `winner` = 낙찰 확정(CONFIRMED)자의 배송/운송장 정보. 결제 미완료 또는 낙찰 전이면 null (슬롯당 최대 1명이라 null 여부로 낙찰 여부 판단)
                            - `winner.trackingNumber` = 호스트가 등록한 운송장 번호. 미등록 시 null
                            - `winner.deliveryStatus` = `SNAPSHOTTED` (운송장 미등록) / `SHIPPING` / `DELIVERED` / `RECEIVED`

                            **응답 예시**
                            ```json
                            {
                              "id": 10,
                              "title": "호두 자랑",
                              "groupName": "IVE",
                              "purchaseSite": "호두네",
                              "status": "CLOSED",
                              "deadline": "2026-05-27T00:00:00Z",
                              "optionCount": 4,
                              "totalParticipationCount": 1,
                              "options": [
                                {
                                  "buncheolMemberId": 101,
                                  "memberId": 1001,
                                  "memberName": "안유진",
                                  "memberImage": "https://cdn.example.com/yujin.png",
                                  "participationCount": 1,
                                  "currentHighestBid": 90000,
                                  "winner": {
                                    "deliveryId": 5001,
                                    "shippingMethod": "GS25_HALF",
                                    "storeName": "GS25 강남역점",
                                    "receiverNickname": "유진팬",
                                    "receiverPhoneNumber": "010-1234-5678",
                                    "trackingNumber": "1234567890",
                                    "deliveryStatus": "SHIPPING"
                                  }
                                },
                                {
                                  "buncheolMemberId": 102,
                                  "memberId": 1002,
                                  "memberName": "레이",
                                  "memberImage": "https://cdn.example.com/rei.png",
                                  "participationCount": 0,
                                  "currentHighestBid": null,
                                  "winner": null
                                }
                              ]
                            }
                            ```

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 호스트가 아님 |
                            """)
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("BuncheolManagementResponse"))
                        .responseFields(
                            fieldWithPath("id").description("분철 ID"),
                            fieldWithPath("title").description("분철 제목"),
                            fieldWithPath("groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("purchaseSite").description("구매처"),
                            fieldWithPath("status")
                                .description(
                                    "분철 진행 상태 (RECRUITING / CLOSED / PAID / SETTLING / FINISHED)"),
                            fieldWithPath("deadline").description("모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("optionCount").description("분철에 등록된 멤버 슬롯 수"),
                            fieldWithPath("totalParticipationCount")
                                .description("분철 전체 참여 수 (활성 입찰 + 결제 대기 + 낙찰 확정)"),
                            fieldWithPath("options").description("옵션(멤버 슬롯) 배열 (등록 순)"),
                            fieldWithPath("options[].buncheolMemberId")
                                .description("분철 멤버 슬롯 ID"),
                            fieldWithPath("options[].memberId").description("그룹 멤버 ID"),
                            fieldWithPath("options[].memberName")
                                .description("멤버 이름. 멤버가 삭제·이동돼 조회되지 않으면 null")
                                .optional(),
                            fieldWithPath("options[].memberImage")
                                .description("멤버 이미지 URL. 멤버가 삭제·이동돼 조회되지 않으면 null")
                                .optional(),
                            fieldWithPath("options[].participationCount")
                                .description("옵션별 참여 수 (활성/결제대기/낙찰 합계)"),
                            fieldWithPath("options[].currentHighestBid")
                                .description("옵션별 최고 제시 금액 (원, 상태 무관). 참여 없으면 null")
                                .optional(),
                            fieldWithPath("options[].winner")
                                .description("낙찰 확정자 배송/운송장 정보. 낙찰 전 또는 결제 미완료 시 null")
                                .optional(),
                            fieldWithPath("options[].winner.deliveryId")
                                .description("배송 ID (운송장 등록 API 호출에 사용)")
                                .optional(),
                            fieldWithPath("options[].winner.shippingMethod")
                                .description("배송방법 스냅샷 (GS25_HALF | CU_HALF)")
                                .optional(),
                            fieldWithPath("options[].winner.storeName")
                                .description("편의점 지점명 스냅샷")
                                .optional(),
                            fieldWithPath("options[].winner.receiverNickname")
                                .description("수령인 닉네임 스냅샷")
                                .optional(),
                            fieldWithPath("options[].winner.receiverPhoneNumber")
                                .description("수령인 전화번호 스냅샷")
                                .optional(),
                            fieldWithPath("options[].winner.trackingNumber")
                                .description("호스트가 등록한 운송장 번호. 미등록 시 null")
                                .optional(),
                            fieldWithPath("options[].winner.deliveryStatus")
                                .description(
                                    "배송 상태 (SNAPSHOTTED / SHIPPING / DELIVERED / RECEIVED)")
                                .optional())
                        .build())));
  }

  @Test
  void 분철_수동_마감() throws Exception {
    mockMvc
        .perform(
            post("/v1/buncheols/{id}/close", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-close",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 수동 마감")
                        .description(
                            """
                            호스트가 `deadline` 도래 전 모집을 조기에 종료한다. 분철의 `status` 가 `CLOSED` 로
                            전이되고 `closedAt` 에 호출 시각이 기록된다.

                            **호출 가능 조건**
                            - 호출 유저가 분철 개최자(host) 본인
                            - 분철 상태가 `RECRUITING`

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 호스트가 아님 |
                            | 409 | `BCH-060` (`BUNCHEOL_NOT_RECRUITING`) | 이미 `CLOSED`/`PAID`/`SETTLING`/`FINISHED`/`CANCELLED` 상태 |
                            """)
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
