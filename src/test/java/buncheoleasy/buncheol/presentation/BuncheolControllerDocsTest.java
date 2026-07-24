package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementParticipantResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberSaleStatus;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationItemResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@DisplayName("BuncheolController 문서화 테스트")
class BuncheolControllerDocsTest extends DocsTestSupport {

  private static final Long HOST_ID = USER_ID;

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;

  private String holdRequestJson() {
    return """
        {
          "groupId": 100,
          "title": "뉴진스 1집 분철",
          "description": "공식 스토어 단독 구성",
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          "minHeadcount": 3,
          "gs25ShippingFee": 3000,
          "cuShippingFee": null,
          "buncheolMembers": [
            {"memberId": 200, "price": 50000}
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
    MockMultipartFile imagePart =
        new MockMultipartFile(
            "images", "album-cover.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/v1/buncheols")
                .file(requestPart)
                .file(imagePart)
                .with(userAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "buncheols-hold",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 개최")
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
                              "minHeadcount": Integer,        // 양수, 분철 진행 최소 인원
                              "gs25ShippingFee": Integer?,    // 양수, gs25/cu 중 최소 1개 필수
                              "cuShippingFee": Integer?,      // 양수, gs25/cu 중 최소 1개 필수
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "price": Long               // 0 이상, 100원 단위. 호스트 고정 금액 (0원은 오픈 이벤트 무료 분철 용도)
                                }
                              ]
                            }
                            ```

                            **images 파트** (**필수**): 이미지 파일 목록, **최소 1장 ~ 최대 5장**. 파트 자체가 누락되면 `400 C-001`,
                            0장이면 `400 BCH-045`

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `request` 검증 실패 또는 `images` 파트 누락 |
                            | 400 | `BCH-027` (`BUNCHEOL_MEMBER_PRICE_INVALID`) | `price` 가 100원 단위가 아님 (음수는 `C-001` 이 먼저 잡는다) |
                            | 400 | `BCH-082` (`BUNCHEOL_MEMBER_FREE_PRICE_MIXED`) | 무료(0원) 슬롯과 유료 슬롯을 섞어 구성 |
                            | 400 | `BCH-045` (`BUNCHEOL_IMAGE_REQUIRED`) | 이미지가 0장 |
                            | 400 | `BCH-040` (`BUNCHEOL_IMAGE_LIMIT_EXCEEDED`) | 이미지가 5장 초과 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
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
            .with(
                request -> {
                  request.setMethod("PUT");
                  return request;
                })
            .with(userAuth());

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
                        .description(
                            """
                            multipart/form-data PUT.

                            모집중(RECRUITING)이고 마감 전인 분철만 수정 가능하며, 제목·설명·이미지만 변경할 수 있다.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "title": String,                // 1~200자
                              "description": String?,         // 선택, 300자 이하
                              "keepImageIds": [Long]          // 유지할 기존 이미지 ID
                            }
                            ```

                            **images 파트** (선택): 새로 업로드할 이미지 파일 목록.
                            `keepImageIds`(해당 분철의 실제 이미지여야 함) + 새 이미지 합이 **최소 1장 ~ 최대 5장** 이어야 한다. 즉 수정 후에도
                            이미지가 0장이 되도록 둘 다 비울 수 없다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `BCH-045` (`BUNCHEOL_IMAGE_REQUIRED`) | 수정 후 남는 이미지가 0장 |
                            | 400 | `BCH-046` (`BUNCHEOL_KEEP_IMAGE_INVALID`) | `keepImageIds` 에 해당 분철의 이미지가 아닌 ID 포함 |
                            | 400 | `BCH-040` (`BUNCHEOL_IMAGE_LIMIT_EXCEEDED`) | 이미지가 5장 초과 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));
  }

  @Test
  void 내_개최_분철_목록_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
    MyHostedBuncheolResponse response =
        new MyHostedBuncheolResponse(
            10L,
            "뉴진스 1집 분철",
            "뉴진스",
            BuncheolStatus.RECRUITING,
            deadline,
            5,
            7L,
            createdAt,
            "https://cdn.example.com/buncheol-10-thumb.jpg");
    given(myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID)).willReturn(List.of(response));

    mockMvc
        .perform(get("/v1/buncheols/me").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-list-my-hosted",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("내가 개최한 분철 목록 조회")
                        .description("마이페이지에서 호스트 본인이 개최한 분철 목록을 최신 개최 순으로 조회한다.")
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("MyHostedBuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].title").description("분철 제목"),
                            fieldWithPath("[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("[].status")
                                .description("분철 진행 상태 (RECRUITING | CONFIRMED | CANCELLED)"),
                            fieldWithPath("[].deadline").description("분철 모집 마감일"),
                            fieldWithPath("[].memberSlotCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].activeParticipationCount")
                                .description("활성 참여자 수 (AWAITING_PAYMENT/CONFIRMED)"),
                            fieldWithPath("[].createdAt").description("분철 개최 일시"),
                            fieldWithPath("[].thumbnailUrl")
                                .description("분철 대표 이미지 URL. 이미지가 없으면 null")
                                .optional())
                        .build())));
  }

  @Test
  void 분철_목록_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    BuncheolSummaryResponse item =
        new BuncheolSummaryResponse(
            10L,
            "뉴진스 1집 분철",
            BuncheolStatus.RECRUITING,
            deadline,
            3,
            true,
            "뉴진스",
            "https://cdn.example.com/buncheol-10-thumb.jpg",
            List.of("민지", "혜인"),
            List.of("혜인"),
            false);
    CursorResponse<BuncheolSummaryResponse> response =
        new CursorResponse<>(List.of(item), "0_2026-05-15T08:00:00Z_10", true);

    given(
            buncheolListQueryService.search(
                HOST_ID,
                new BuncheolSearchCondition(100L, 200L, "뉴진스"),
                BuncheolListCursor.parse("0_2026-05-15T08:00:00Z_15"),
                20))
        .willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols")
                .queryParam("groupId", "100")
                .queryParam("memberId", "200")
                .queryParam("keyword", "뉴진스")
                .queryParam("cursor", "0_2026-05-15T08:00:00Z_15")
                .queryParam("size", "20")
                .with(userAuth()))
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
                            - 정렬: 3개 그룹 순 — **① 모집중(`RECRUITING`) 을 최신 개최순(`createdAt DESC`)** 으로 먼저, **② 마감(`CONFIRMED`) 을 마감 임박순(`deadline DESC`, 현재와 가까운 마감일 우선)** 으로, 마지막에 **③ 인원미달 자동취소(`CANCELLED`) 를 `deadline DESC`** 로 잇는다. 세 그룹 모두 동일 시각은 `id DESC` 로 끊는다. 카드별 `items[].status` 로 모집중/마감/취소를 구분(배지·섹션)할 수 있다
                            - 노출 상태: 개최자 직접 취소(`HOST_CANCELLED`)만 제외한 모든 status (`RECRUITING` / `CONFIRMED` / `CANCELLED`(인원미달 자동취소))
                            - 페이지: **커서 기반 무한스크롤**. 응답의 `nextCursor` 를 다음 요청의 `cursor` 로 그대로 전달 (불투명 토큰 — 형식에 의존하지 말 것)
                            - `nextCursor` 형식: `<groupRank>_<sortAt Instant ISO-8601>_<id>` (groupRank 0=모집중·sortAt=createdAt, 1=마감·sortAt=deadline, 2=인원미달 취소·sortAt=deadline. 예: `0_2026-05-15T08:00:00Z_10`)
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
                        .requestHeaders(optionalUserAuthorizationHeader())
                        .queryParameters(
                            parameterWithName("groupId").description("그룹 ID 필터").optional(),
                            parameterWithName("memberId").description("단일 멤버 ID 필터").optional(),
                            parameterWithName("keyword")
                                .description("title/description 부분 일치 키워드")
                                .optional(),
                            parameterWithName("cursor")
                                .description("직전 응답의 `nextCursor` 를 그대로 전달하는 불투명 토큰. 첫 페이지는 생략")
                                .optional(),
                            parameterWithName("size")
                                .description("페이지 크기 (기본 20, 1~50)")
                                .optional())
                        .responseSchema(Schema.schema("BuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("items").description("분철 카드 배열"),
                            fieldWithPath("items[].id").description("분철 ID"),
                            fieldWithPath("items[].title").description("분철 제목"),
                            fieldWithPath("items[].status")
                                .description(
                                    "분철 진행 상태 — `RECRUITING`(모집중) | `CONFIRMED`(마감) |"
                                        + " `CANCELLED`(인원미달 자동취소). 목록은 `HOST_CANCELLED`(개최자 취소)만"
                                        + " 제외하므로 이 셋만 내려간다"),
                            fieldWithPath("items[].deadline")
                                .description("분철 모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("items[].minHeadcount").description("분철 진행 최소 인원"),
                            fieldWithPath("items[].bookmarked")
                                .description("호출 사용자의 본인 찜 여부 (비로그인이면 항상 false)"),
                            fieldWithPath("items[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("items[].thumbnailUrl")
                                .description("대표이미지 URL — 분철에 등록된 첫 이미지. 이미지 없으면 null")
                                .optional(),
                            fieldWithPath("items[].memberNames")
                                .description("분철에 포함된 전체 멤버 이름 (호스트 등록 슬롯 순)"),
                            fieldWithPath("items[].availableMemberNames")
                                .description("아직 안 팔린(참여 가능한) 멤버 이름 (호스트 등록 슬롯 순)"),
                            fieldWithPath("items[].shippingFeePaybackTarget")
                                .description(
                                    "오픈 이벤트 배송비 환급 대상 분철 여부 (전 슬롯 0원 + 이벤트 활성)."
                                        + " 무료 분철 배지 판정용"),
                            fieldWithPath("nextCursor")
                                .description(
                                    "다음 페이지 커서 — `<groupRank>_<sortAt>_<id>` 불투명 토큰. `hasNext=false` 면 null")
                                .optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"))
                        .build())));
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
            3,
            1,
            List.of("https://cdn.example.com/img1.jpg"),
            List.of(
                new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000),
                new ShippingOptionResponse(ShippingMethod.CU_HALF, 4000)),
            List.of(
                new BuncheolMemberDetailResponse(
                    101L, 1001L, "민지", "https://cdn.example.com/minji.png", 40_000L,
                    BuncheolMemberSaleStatus.AWAITING_PAYMENT,
                    Instant.parse("2026-05-20T10:30:00Z"),
                    true),
                new BuncheolMemberDetailResponse(
                    102L, 1002L, "해린", "https://cdn.example.com/haerin.png", 30_000L,
                    BuncheolMemberSaleStatus.AVAILABLE, null, false)),
            true,
            new MyParticipationSummaryResponse(
                1,
                List.of(
                    new MyParticipationItemResponse(
                        601L, 101L, ParticipationStatus.AWAITING_PAYMENT))));
    given(buncheolDetailQueryService.getDetail(10L, HOST_ID)).willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols/{id}", 10L).with(userAuth()))
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
                            - `CANCELLED`(인원미달 자동취소) 상태 분철도 200 으로 응답하며 `status` 로 구분.
                              단, 개최자가 직접 취소한(`HOST_CANCELLED`) 분철은 존재하지 않는 것처럼 **404 (`BCH-043`)** 로 응답한다
                            - `minHeadcount` 는 분철 진행 최소 인원, `confirmedCount` 는 현재 입금확인된 참여자 수
                            - 멤버별 `price` 는 호스트가 설정한 해당 멤버 슬롯의 고정 금액 (원, **0 이상·100원 단위** — 0원 슬롯은 오픈 이벤트 무료 분철 용도)
                            - 멤버별 `saleStatus` 는 판매 상태 — `AVAILABLE`(공석, 참여 가능) /
                              `AWAITING_PAYMENT`(누군가 선점 후 입금 확인 대기 중, 기한 초과 시 다시 공석) / `SOLD`(입금확인 완료)
                            - 멤버별 `paymentDueAt` 은 선점한 참여의 입금 기한 (UTC ISO-8601). `AWAITING_PAYMENT` 일 때만
                              내려가며, 이 시각이 지나면 슬롯이 다시 공석으로 풀린다 — 대기 중인 유저에게 재시도 시점 안내용
                            - 멤버별 `participatedByMe` 는 그 슬롯을 점유한 활성 참여(선점·구매)가 호출 유저의 것인지 여부.
                              내 선점("내가 입금 대기중")과 타인 선점("입금 대기중") UI 워딩 구분용. 비로그인 호출이면 항상 false
                            - `hostedByMe` 는 호출 유저가 개최자인지 여부 (비로그인 호출이면 항상 false)
                            - 로그인 유저 한정: `myParticipation.participations[]` 는 내 활성 참여 목록 (멤버 슬롯별 1건)
                            - `myParticipation.participatedMemberCount` 는 이 분철에서 내가 활성 참여 중인 멤버 슬롯 수
                            - `myParticipation.participations[].participationId` 는 참여 취소·상세 조회 API 호출에 사용

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
                              "minHeadcount": 3,
                              "confirmedCount": 1,
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
                                  "price": 40000,
                                  "saleStatus": "AWAITING_PAYMENT",
                                  "paymentDueAt": "2026-05-20T10:30:00Z",
                                  "participatedByMe": true
                                },
                                {
                                  "buncheolMemberId": 102,
                                  "memberId": 1002,
                                  "memberName": "해린",
                                  "memberImage": "https://cdn.example.com/haerin.png",
                                  "price": 30000,
                                  "saleStatus": "AVAILABLE",
                                  "paymentDueAt": null,
                                  "participatedByMe": false
                                }
                              ],
                              "hostedByMe": true,
                              "myParticipation": {
                                "participatedMemberCount": 1,
                                "participations": [
                                  {"participationId": 601, "buncheolMemberId": 101, "status": "AWAITING_PAYMENT"}
                                ]
                              }
                            }
                            ```

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철, 또는 개최자가 직접 취소(`HOST_CANCELLED`)한 분철 |
                            """)
                        .requestHeaders(optionalUserAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .responseSchema(Schema.schema("BuncheolDetailResponse"))
                        .responseFields(
                            fieldWithPath("id").description("분철 ID"),
                            fieldWithPath("title").description("분철 제목"),
                            fieldWithPath("groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("purchaseSite").description("구매처"),
                            fieldWithPath("deadline").description("모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("description").description("분철 설명").optional(),
                            fieldWithPath("status")
                                .description("분철 진행 상태 (RECRUITING / CONFIRMED / CANCELLED)"),
                            fieldWithPath("minHeadcount").description("분철 진행 최소 인원"),
                            fieldWithPath("confirmedCount").description("현재 입금확인된 참여자 수"),
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
                            fieldWithPath("members[].price")
                                .description("호스트가 설정한 해당 멤버 슬롯의 고정 금액 (원, 0 이상·100원 단위)"),
                            fieldWithPath("members[].saleStatus")
                                .description(
                                    "판매 상태 (AVAILABLE=공석 | AWAITING_PAYMENT=입금 확인 대기 중 | SOLD=판매 완료)"),
                            fieldWithPath("members[].paymentDueAt")
                                .description(
                                    "선점한 참여의 입금 기한 (UTC ISO-8601). AWAITING_PAYMENT 일 때만 값이 있고,"
                                        + " 이 시각이 지나면 슬롯이 다시 공석으로 풀린다")
                                .optional(),
                            fieldWithPath("members[].participatedByMe")
                                .description(
                                    "그 슬롯을 점유한 활성 참여(선점·구매)가 호출 유저의 것인지 여부"
                                        + " (비로그인 호출이면 항상 false)"),
                            fieldWithPath("hostedByMe")
                                .description("호출 유저가 개최자인지 여부 (비로그인은 false)"),
                            fieldWithPath("myParticipation")
                                .description("로그인 유저의 활성 참여 요약. 비로그인이면 null")
                                .optional(),
                            fieldWithPath("myParticipation.participatedMemberCount")
                                .description("이 분철에서 내가 활성 참여 중인 멤버 슬롯 수")
                                .optional(),
                            fieldWithPath("myParticipation.participations")
                                .description("내 활성 참여 목록 (멤버 슬롯별 1건)")
                                .optional(),
                            fieldWithPath("myParticipation.participations[].participationId")
                                .description("참여 ID (참여 취소·상세 조회 API에 사용)")
                                .optional(),
                            fieldWithPath("myParticipation.participations[].buncheolMemberId")
                                .description("내가 참여한 멤버 슬롯 ID")
                                .optional(),
                            fieldWithPath("myParticipation.participations[].status")
                                .description("참여 상태 (AWAITING_PAYMENT | CONFIRMED)")
                                .optional())
                        .build())));
  }

  @Test
  void 분철_취소() throws Exception {
    mockMvc
        .perform(
            delete("/v1/buncheols/{id}", 10L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 취소")
                        .description(
                            """
                            호스트가 자신이 개최한 분철을 취소한다.

                            모집중(`RECRUITING`) 상태에서만 취소할 수 있으며, 성공 시 `RECRUITING` → `HOST_CANCELLED` 로
                            전이된다. 마감 판정 등과 경합해 이미 다른 상태로 전이된 뒤라면 409 로 실패한다.
                            취소된 분철은 목록·상세에서 노출되지 않는다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 409 | `BCH-050` (`BUNCHEOL_CANCEL_NOT_ALLOWED`) | 모집중(`RECRUITING`) 상태가 아니어서 취소 불가 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));
  }

  @Test
  void 개최자_분철_관리_화면_조회() throws Exception {
    Instant deadline = Instant.parse("2026-05-27T00:00:00Z");
    BuncheolManagementParticipantResponse confirmed =
        new BuncheolManagementParticipantResponse(
            601L,
            "유진팬",
            101L,
            "안유진",
            93_000L,
            ParticipationStatus.CONFIRMED,
            Instant.parse("2026-05-28T00:00:00Z"),
            Instant.parse("2026-05-27T10:00:00Z"),
            new RefundAccountResponse("국민은행", "12345678", "유진팬"),
            new ManagementDeliveryResponse(
                5001L,
                ShippingMethod.GS25_HALF,
                "GS25 강남역점",
                "유진팬",
                "010-1234-5678",
                "1234567890",
                DeliveryStatus.SHIPPING));
    BuncheolManagementParticipantResponse awaiting =
        new BuncheolManagementParticipantResponse(
            602L,
            "레이팬",
            102L,
            "레이",
            53_000L,
            ParticipationStatus.AWAITING_PAYMENT,
            Instant.parse("2026-05-26T00:30:00Z"),
            null,
            new RefundAccountResponse("신한은행", "87654321", "레이팬"),
            null);
    BuncheolManagementResponse response =
        new BuncheolManagementResponse(
            10L,
            "호두 자랑",
            "IVE",
            "호두네",
            BuncheolStatus.CONFIRMED,
            deadline,
            4,
            4,
            1,
            List.of(confirmed, awaiting));
    given(buncheolManagementQueryService.getManagement(10L, HOST_ID)).willReturn(response);

    mockMvc
        .perform(
            get("/v1/buncheols/{id}/management", 10L).with(userAuth()))
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
                            - 분철 상태(`RECRUITING` / `CONFIRMED` / `CANCELLED` / `HOST_CANCELLED`) 무관하게 호출 가능
                            - `memberCount` = 분철에 등록된 멤버 슬롯 수
                            - `minHeadcount` = 분철 진행 최소 인원, `confirmedCount` = 입금확인된 참여자 수
                            - `participants[]` = 활성 참여자 목록 (입금확인 대상 AWAITING_PAYMENT + 확정 CONFIRMED)
                            - `participants[].refundAccount` = 참여자가 입력한 환불 계좌 (분철 취소 시 운영자가 환불)
                            - `participants[].delivery` = 배송 스냅샷. 입금확인(CONFIRMED) 참여에만 생성되며 그 전(AWAITING_PAYMENT)에는 null
                            - `participants[].participationId` = **개최자 입금확인 API(`POST /v1/participations/{id}/confirm`) 의 대상 식별자**

                            **응답 예시**
                            ```json
                            {
                              "id": 10,
                              "title": "호두 자랑",
                              "groupName": "IVE",
                              "purchaseSite": "호두네",
                              "status": "CONFIRMED",
                              "deadline": "2026-05-27T00:00:00Z",
                              "minHeadcount": 4,
                              "memberCount": 4,
                              "confirmedCount": 1,
                              "participants": [
                                {
                                  "participationId": 601,
                                  "participantNickname": "유진팬",
                                  "buncheolMemberId": 101,
                                  "memberName": "안유진",
                                  "amount": 93000,
                                  "status": "CONFIRMED",
                                  "dueAt": "2026-05-28T00:00:00Z",
                                  "confirmedAt": "2026-05-27T10:00:00Z",
                                  "refundAccount": {"bank": "국민은행", "account": "12345678", "holder": "유진팬"},
                                  "delivery": {
                                    "deliveryId": 5001,
                                    "shippingMethod": "GS25_HALF",
                                    "storeName": "GS25 강남역점",
                                    "receiverNickname": "유진팬",
                                    "receiverPhoneNumber": "010-1234-5678",
                                    "trackingNumber": "1234567890",
                                    "status": "SHIPPING"
                                  }
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
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .responseSchema(Schema.schema("BuncheolManagementResponse"))
                        .responseFields(
                            fieldWithPath("id").description("분철 ID"),
                            fieldWithPath("title").description("분철 제목"),
                            fieldWithPath("groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("purchaseSite").description("구매처"),
                            fieldWithPath("status")
                                .description(
                                    "분철 진행 상태 (RECRUITING / CONFIRMED / CANCELLED / HOST_CANCELLED)"),
                            fieldWithPath("deadline").description("모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("minHeadcount").description("분철 진행 최소 인원"),
                            fieldWithPath("memberCount").description("분철에 등록된 멤버 슬롯 수"),
                            fieldWithPath("confirmedCount").description("입금확인된 참여자 수"),
                            fieldWithPath("participants")
                                .description("활성 참여자 배열 (AWAITING_PAYMENT + CONFIRMED)"),
                            fieldWithPath("participants[].participationId")
                                .description("참여 ID (개최자 입금확인 API 호출에 사용)"),
                            fieldWithPath("participants[].participantNickname")
                                .description("참여자 닉네임. 조회 불가 시 null")
                                .optional(),
                            fieldWithPath("participants[].buncheolMemberId")
                                .description("분철 멤버 슬롯 ID"),
                            fieldWithPath("participants[].memberName")
                                .description("멤버 이름. 멤버가 삭제·이동돼 조회되지 않으면 null")
                                .optional(),
                            fieldWithPath("participants[].amount")
                                .description("참여 금액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("participants[].status")
                                .description("참여 상태 (AWAITING_PAYMENT / CONFIRMED)"),
                            fieldWithPath("participants[].dueAt")
                                .description("입금 기한 (UTC ISO-8601)")
                                .optional(),
                            fieldWithPath("participants[].confirmedAt")
                                .description("입금확인 시각. 미확인 시 null")
                                .optional(),
                            fieldWithPath("participants[].refundAccount")
                                .description("참여자가 입력한 환불 계좌 (분철 취소 시 환불에 사용)"),
                            fieldWithPath("participants[].refundAccount.bank")
                                .description("환불 은행명"),
                            fieldWithPath("participants[].refundAccount.account")
                                .description("환불 계좌번호"),
                            fieldWithPath("participants[].refundAccount.holder")
                                .description("환불 예금주"),
                            fieldWithPath("participants[].delivery")
                                .description("배송 스냅샷. 입금확인(CONFIRMED) 참여에만 생성되며 그 전에는 null")
                                .optional(),
                            fieldWithPath("participants[].delivery.deliveryId")
                                .description("배송 ID (운송장 등록 API 호출에 사용)")
                                .optional(),
                            fieldWithPath("participants[].delivery.shippingMethod")
                                .description("배송방법 (GS25_HALF | CU_HALF)")
                                .optional(),
                            fieldWithPath("participants[].delivery.storeName")
                                .description("편의점 지점명")
                                .optional(),
                            fieldWithPath("participants[].delivery.receiverNickname")
                                .description("수령인 닉네임 스냅샷")
                                .optional(),
                            fieldWithPath("participants[].delivery.receiverPhoneNumber")
                                .description("수령인 전화번호 스냅샷")
                                .optional(),
                            fieldWithPath("participants[].delivery.trackingNumber")
                                .description("호스트가 등록한 운송장 번호. 미등록 시 null")
                                .optional(),
                            fieldWithPath("participants[].delivery.status")
                                .description(
                                    "배송 상태 (SNAPSHOTTED / SHIPPING / DELIVERED / RECEIVED)")
                                .optional())
                        .build())));
  }
}
