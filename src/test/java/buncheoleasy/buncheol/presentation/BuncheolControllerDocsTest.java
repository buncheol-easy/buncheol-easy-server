package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.BuncheolConfirmResult;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolImageResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementParticipantResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberSaleStatus;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
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
import org.springframework.restdocs.payload.JsonFieldType;
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
          "thumbnailIndex": 0,
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
          "keepImageIds": [1, 2],
          "thumbnailImageId": 2
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

    given(buncheolService.holdBuncheol(any(), any(), any())).willReturn(10L);

    mockMvc
        .perform(
            multipart("/v1/buncheols")
                .file(requestPart)
                .file(imagePart)
                .with(userAuth()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.buncheolId").value(10))
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
                              "title": String,                // 1~64자
                              "description": String?,         // 선택, 700자 이하
                              "purchaseSite": String,         // 1~200자
                              "deadline": Instant,            // 미래 시점 (UTC ISO-8601, 예: 2026-06-01T03:00:00Z)
                              "minHeadcount": Integer,        // 양수, 분철 진행 최소 인원
                              "gs25ShippingFee": Integer?,    // 0 이상 (0원 = 무료 배송), gs25/cu 중 최소 1개 필수
                              "cuShippingFee": Integer?,      // 0 이상 (0원 = 무료 배송), gs25/cu 중 최소 1개 필수
                              "openChatUrl": String?,         // 선택, https://open.kakao.com/ 시작·200자 이하 — 참여자 소통 채널
                              "flowType": String?,            // 선택 ("LEGACY"|"C2C") — null 이면 서버 결정: 일반 유저 = C2C 강제, 운영진(can_host) = LEGACY 기본에 C2C 선택 가능
                              "thumbnailIndex": Integer,      // 필수, 대표사진으로 쓸 images 파트 내 인덱스(0-base)
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "price": Long,              // 0 이상, 100원 단위. 호스트 고정 금액 (0원은 오픈 이벤트 무료 분철 용도)
                                  "accessType": String?       // 선택 ("OPEN"|"CODE_ONLY") — 생략 시 OPEN(선착순). CODE_ONLY 는 참여 코드 보유자 전용 배정 슬롯이며 운영진(LEGACY) 개최에서만 지정할 수 있다
                                }
                              ]
                            }
                            ```

                            **images 파트** (**필수**): 이미지 파일 목록, **최소 1장 ~ 최대 5장**. 파트 자체가 누락되면 `400 C-001`,
                            0장이면 `400 BCH-045`

                            이미지는 **업로드한 순서 그대로 저장·노출**된다. 대표사진은 순서를 바꾸지 않고 `thumbnailIndex`
                            로 **반드시 지정**하며(누락 시 `400 C-001`), 목록 카드의 `thumbnailUrl` 에만 반영된다.
                            음수 `thumbnailIndex` 는 DTO 검증(`@PositiveOrZero`)이 먼저 걸러 `400 C-001` 로 응답한다 —
                            `BCH-047` 은 images 파트 개수를 넘는 **범위 초과**에만 해당한다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `request` 검증 실패 또는 `images` 파트 누락 |
                            | 400 | `BCH-027` (`BUNCHEOL_MEMBER_PRICE_INVALID`) | `price` 가 100원 단위가 아님 (음수는 `C-001` 이 먼저 잡는다) |
                            | 400 | `BCH-045` (`BUNCHEOL_IMAGE_REQUIRED`) | 이미지가 0장 |
                            | 400 | `BCH-040` (`BUNCHEOL_IMAGE_LIMIT_EXCEEDED`) | 이미지가 5장 초과 |
                            | 400 | `BCH-047` (`BUNCHEOL_THUMBNAIL_INDEX_INVALID`) | `thumbnailIndex` 가 images 파트 범위를 벗어남 |
                            | 400 | `BCH-088` (`BUNCHEOL_OPEN_CHAT_URL_INVALID`) | `openChatUrl` 형식 위반 |
                            | 403 | `USR-031` (`USER_CANNOT_HOST`) | 일반 유저가 `LEGACY` 개최를 요청 (운영진 전용 방식) |
                            | 409 | `BCH-103` (`PARTICIPATION_CODE_MEMBER_NOT_CODE_ONLY`) | C2C 개최에 `accessType: "CODE_ONLY"` 슬롯을 포함 (코드 발급이 운영진 전용이라 영구 잠긴 슬롯이 된다) |
                            | 409 | `BCH-109` (`PARTICIPATION_CODE_MEMBER_NOT_FREE`) | `accessType: "CODE_ONLY"` 슬롯의 `price` 가 0이 아님 (코드 참여는 무상 제공 전제) |
                            | 403 | `USR-018` (`USER_PROFILE_IS_NOT_COMPLETE`) | C2C 개최 자격 — 가입 미완료(전화번호 미등록). 운영진의 C2C 선택에도 적용 |
                            | 409 | `BCH-089` (`BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED`) | 일반 유저 활성(모집중·입금 수집중) 개최 수 상한 초과 |
                            | 409 | `USR-032` (`USER_AGE_NOT_VERIFIED`) | C2C 개최 자격 — 연령대 미확인. 카카오 로그인 재동의(연령대 제공)로 해소 가능 |
                            | 403 | `USR-033` (`USER_NOT_ADULT`) | C2C 개최 자격 — 미성년자는 개최 불가 |
                            | 409 | `USR-025` (`USER_BANK_ACCOUNT_NOT_REGISTERED`) | 정산 계좌 미등록 (LEGACY·C2C 공통) |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .responseFields(
                            fieldWithPath("buncheolId")
                                .type(JsonFieldType.NUMBER)
                                .description(
                                    "생성된 분철 id. 생성 직후 상세·관리 화면으로 바로 이동할 때 쓴다 (docs/53 Q-15 — 목록 재조회 후 제목 매칭 불필요)"))
                        .responseSchema(Schema.schema("HoldBuncheolResponse"))
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

                            모집중(RECRUITING)이고 마감 전인 분철만 수정 가능하며, 제목·설명·이미지·오픈채팅 링크만 변경할 수 있다.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "title": String,                // 1~64자
                              "description": String?,         // 선택, 700자 이하
                              "keepImageIds": [Long],         // 유지할 기존 이미지 ID
                              "thumbnailImageId": Long?,      // 유지 이미지 중 대표사진으로 지정할 ID (keepImageIds 에 포함돼야 함)
                              "thumbnailIndex": Integer?,     // 신규 images 파트 중 대표사진으로 쓸 인덱스(0-base) — 둘 중 정확히 하나 필수
                              "openChatUrl": String?          // null = 기존 값 유지, 빈 문자열/공백 = 링크 제거, 값 = https://open.kakao.com/ 형식 검증 후 교체
                            }
                            ```

                            **images 파트** (선택): 새로 업로드할 이미지 파일 목록.
                            `keepImageIds`(해당 분철의 실제 이미지여야 함) + 새 이미지 합이 **최소 1장 ~ 최대 5장** 이어야 한다. 즉 수정 후에도
                            이미지가 0장이 되도록 둘 다 비울 수 없다.

                            이미지 순서는 항상 **등록 순(id ASC, 기존 이미지 → 새 이미지 업로드 순)** 으로 유지된다 —
                            `keepImageIds` 의 순서를 바꿔 보내도 **재정렬되지 않고**, 신규 이미지는 항상 뒤에 붙는다. 대표사진은
                            `thumbnailImageId`(기존 이미지)와 `thumbnailIndex`(신규 이미지) 중 **정확히 하나를 반드시
                            지정**해야 한다 (둘 다 누락 시 `BCH-083`, 동시 지정 시 `BCH-049`).

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `BCH-045` (`BUNCHEOL_IMAGE_REQUIRED`) | 수정 후 남는 이미지가 0장 |
                            | 400 | `BCH-046` (`BUNCHEOL_KEEP_IMAGE_INVALID`) | `keepImageIds` 에 해당 분철의 이미지가 아닌 ID 포함 |
                            | 400 | `BCH-040` (`BUNCHEOL_IMAGE_LIMIT_EXCEEDED`) | 이미지가 5장 초과 |
                            | 400 | `BCH-047` (`BUNCHEOL_THUMBNAIL_INDEX_INVALID`) | `thumbnailIndex` 가 신규 images 파트 범위를 벗어남 |
                            | 400 | `BCH-088` (`BUNCHEOL_OPEN_CHAT_URL_INVALID`) | `openChatUrl` 형식 위반 |
                            | 400 | `BCH-048` (`BUNCHEOL_THUMBNAIL_IMAGE_INVALID`) | `thumbnailImageId` 가 `keepImageIds` 에 없음 |
                            | 400 | `BCH-049` (`BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED`) | `thumbnailImageId` 와 `thumbnailIndex` 동시 지정 |
                            | 400 | `BCH-083` (`BUNCHEOL_THUMBNAIL_REQUIRED`) | 대표사진 지정(`thumbnailImageId`/`thumbnailIndex`) 누락 |
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
            "https://cdn.example.com/buncheol-10-thumb.jpg",
            FlowType.LEGACY,
            BuncheolHostCancellability.CANCELLABLE);
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
                                .optional(),
                            fieldWithPath("[].flowType")
                                .description("분철 진행 방식 (LEGACY: 즉시 입금 | C2C: 신청→확정→입금 직거래)"),
                            fieldWithPath("[].cancellability")
                                .description(
                                    """
                                    개최자 취소 가능 여부와 사유 (docs/56 S-2). 취소 API 게이트와 같은 판정이라 \
                                    화면은 이 값만 보고 삭제 버튼을 노출하면 된다.
                                    CANCELLABLE: 취소 가능 |
                                    BLOCKED_BY_STATUS: 진행확정 이후·이미 취소됨 (BCH-050) |
                                    BLOCKED_BY_CONFIRMED_PAYMENT: 입금확인 참여 1건 이상 (BCH-093)"""))
                        .build())));
  }

  @Test
  void 개최_자격_사전_조회() throws Exception {
    given(buncheolService.getHostingEligibility(HOST_ID))
        .willReturn(
            HostingEligibilityResponse.blocked(HostingEligibilityResponse.Reason.NOT_ADULT));

    mockMvc
        .perform(get("/v1/buncheols/hosting-eligibility").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-hosting-eligibility",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("개최 자격 사전 조회")
                        .description(
                            """
                            개최 폼 진입 시점에 개최 자격을 미리 판정한다 (docs/53 Q-07). 폼을 다 채운 뒤 제출에서야
                            자격 실패가 드러나던 것을 막기 위한 조회용 API 라, 부적격이어도 **200 으로 사유를 담아** 응답한다.

                            판정은 개최 요청(`POST /v1/buncheols`) 이 던지는 검사를 **같은 순서**로 재현한다. 다만 조회 시점
                            스냅샷이므로 **최종 차단은 개최 요청 시점의 게이트**가 한다(그 사이 상태가 바뀔 수 있다).

                            운영진(`can_host`)은 기본 LEGACY 개최라 C2C 자격 게이트·활성 개최 상한이 적용되지 않는다.
                            정산 계좌만 LEGACY·C2C 공통 요구로 모두에게 검사한다. **판정은 LEGACY 기준**이라, 운영진이
                            개최 요청에서 `flowType=C2C` 를 고르는 경우의 추가 요구(가입 완료 — 403 `USR-018`)는 이 응답에 반영되지 않는다.

                            **`reason` 과 개최 요청 시 대응 에러**
                            | reason | 의미 | 개최 요청 시 |
                            |--------|------|--------------|
                            | `NOT_OPEN_YET` | 회원 개최 오픈 전(서비스 스위치 off) — 사용자가 고칠 것이 없다 | 409 `USR-035` |
                            | `PHONE_REQUIRED` | 가입 미완료(전화번호 미등록) | 403 `USR-018` |
                            | `AGE_UNVERIFIED` | 연령대 미확인 — 카카오 재로그인 동의로 회복 가능 | 409 `USR-032` |
                            | `NOT_ADULT` | 미성년 확정 — 개최 불가 | 403 `USR-033` |
                            | `LIMIT_EXCEEDED` | 활성(모집중·입금 수집중) 개최 수 상한 초과 | 409 `BCH-089` |
                            | `BANK_ACCOUNT_REQUIRED` | 정산 계좌 미등록 (LEGACY·C2C 공통) | 409 `USR-025` |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("HostingEligibilityResponse"))
                        .responseFields(
                            fieldWithPath("eligible")
                                .type(JsonFieldType.BOOLEAN)
                                .description("개최 가능 여부. true 면 폼을 열어도 되고, false 면 사유별 안내로 막는다"),
                            fieldWithPath("reason")
                                .type(JsonFieldType.STRING)
                                .description(
                                    "부적격 사유 (NOT_OPEN_YET | PHONE_REQUIRED | AGE_UNVERIFIED | NOT_ADULT | LIMIT_EXCEEDED | BANK_ACCOUNT_REQUIRED). eligible 이 true 면 null")
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
            FlowType.C2C,
            deadline,
            3,
            true,
            "뉴진스",
            "https://cdn.example.com/buncheol-10-thumb.jpg",
            List.of("민지", "혜인"),
            List.of("혜인"),
            false,
            true);
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
                                .description(
                                    "검색 키워드. 분철 제목·그룹명·멤버명은 공백과 구두점(. _ - ( ) [ ] ·)을 무시하고"
                                        + " 부분 일치하며(\"스트레이 키즈\" == \"스트레이키즈\"), 분철 설명은 원문 부분 일치다.")
                                .optional(),
                            parameterWithName("onlyFavoriteGroups")
                                .description(
                                    "true 면 로그인 사용자가 최애로 등록한 그룹의 분철만 반환한다."
                                        + " 비로그인이거나 최애가 0개면 빈 목록 (기본 false)")
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
                                        + " `CANCELLED`(인원미달 자동취소). 목록은 이 셋만 내려간다 —"
                                        + " `HOST_CANCELLED`(개최자 취소)와 `PAYMENT_COLLECTING`(C2C 입금"
                                        + " 수집중)은 제외된다 (수집중 분철의 목록 노출은 후속 검토)"),
                            fieldWithPath("items[].flowType")
                                .description(
                                    "참여 플로우 — `LEGACY`(운영진 개최, 즉시 입금) | `C2C`(사용자 개최,"
                                        + " 신청→확정→입금). 카드 배지·dim 판정을 상세와 동일 기준으로 통일하기"
                                        + " 위한 필드"),
                            fieldWithPath("items[].deadline")
                                .description("분철 모집 마감 시각 (UTC ISO-8601)"),
                            fieldWithPath("items[].minHeadcount").description("분철 진행 최소 인원"),
                            fieldWithPath("items[].bookmarked")
                                .description("호출 사용자의 본인 찜 여부 (비로그인이면 항상 false)"),
                            fieldWithPath("items[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("items[].thumbnailUrl")
                                .description("대표사진 URL — 개최자가 지정한 대표사진(미지정 시 첫 이미지). 이미지 없으면 null")
                                .optional(),
                            fieldWithPath("items[].memberNames")
                                .description("분철에 포함된 전체 멤버 이름 (호스트 등록 슬롯 순)"),
                            fieldWithPath("items[].availableMemberNames")
                                .description(
                                    "지금 신청할 수 있는 멤버 이름 (호스트 등록 슬롯 순). 신규 참여를 받지 않는 분철(취소·진행확정·마감 경과)은 슬롯이 비어 있어도 빈 배열이라 status 로 다시 거를 필요가 없다"),
                            fieldWithPath("items[].shippingFeePaybackTarget")
                                .description(
                                    "오픈 이벤트 배송비 환급 대상 분철 여부 (전 슬롯 0원 + 이벤트 활성)."
                                        + " 무료 분철 배지 판정용"),
                            fieldWithPath("items[].freeShippingEventTarget")
                                .description(
                                    "배송비 0원 이벤트 대상 분철 여부 — 운영진(`LEGACY`) 분철이면서 이용 가능한"
                                        + " 배송수단의 배송비가 모두 0원(등록하지 않은 배송수단은 판정에서 제외)."
                                        + " 카드의 \"배송비 0원 이벤트\" 배지 판정용. 일반 유저가 배송비를 0원으로"
                                        + " 잡은 C2C 분철은 제외"),
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
            List.of(
                new BuncheolImageResponse(11L, "https://cdn.example.com/img1.jpg", false),
                new BuncheolImageResponse(12L, "https://cdn.example.com/img2.jpg", true)),
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
                        601L, 101L, ParticipationStatus.AWAITING_PAYMENT))), FlowType.LEGACY, null, null);
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
                            - `images` 는 **등록 순(업로드 순)** 으로 내려간다 — 대표사진이라고 앞으로 당겨지지 않는다.
                              대표사진은 `images[].thumbnail` 플래그로만 식별하며, 이미지가 있으면 정확히 1장만 `true` 다
                              (수정 화면의 유지 이미지 `keepImageIds`·대표사진 프리셀렉트에는 `images[].id` 를 사용)
                            - 멤버별 `price` 는 호스트가 설정한 해당 멤버 슬롯의 고정 금액 (원, **0 이상·100원 단위** — 0원 슬롯은 오픈 이벤트 무료 분철 용도)
                            - 멤버별 `saleStatus` 는 판매 상태 — `AVAILABLE`(공석, 참여 가능) / `APPLIED`(C2C 무입금 신청으로 선점) /
                              `AWAITING_PAYMENT`(누군가 선점 후 입금 확인 대기 중, 기한 초과 시 다시 공석) / `SOLD`(입금확인 완료) /
                              `CLOSED`(신규 참여를 받지 않는 분철의 공석 — 진행확정·취소 이후. 참여자가 없지만 신청도 불가) /
                              `CODE_ONLY`(참여 코드 보유자에게 배정된 공석 — 선착순으로는 참여 불가. "마감"이 아니라
                              "배정된 자리"로 표시할 것)
                            - 멤버별 `paymentDueAt` 은 선점한 참여의 입금 기한 (UTC ISO-8601). `AWAITING_PAYMENT` 일 때만
                              내려가며, 이 시각이 지나면 슬롯이 공석으로 풀린다 — 대기 중인 유저에게 재시도 시점 안내용.
                              단 그 시점에 분철이 신규 참여를 받지 않는 상태면 슬롯은 `AVAILABLE` 이 아니라 `CLOSED` 가 되므로,
                              "이 시각에 다시 신청 가능" 으로 단정하지 말고 재조회 결과의 `saleStatus` 를 따를 것
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
                              "images": [
                                {"id": 11, "url": "https://cdn.example.com/img1.jpg", "thumbnail": false},
                                {"id": 12, "url": "https://cdn.example.com/img2.jpg", "thumbnail": true}
                              ],
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
                            fieldWithPath("images")
                                .description("분철 이미지 배열 (등록 순 — 대표사진 순서 우대 없음)"),
                            fieldWithPath("images[].id")
                                .description("이미지 ID (수정 시 keepImageIds·thumbnailImageId 로 사용)"),
                            fieldWithPath("images[].url").description("이미지 URL"),
                            fieldWithPath("images[].thumbnail")
                                .description(
                                    "대표사진 여부 — 이미지가 있으면 정확히 1장만 true (개최/수정 시 지정 필수)"),
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
                                    "판매 상태 (AVAILABLE=공석 | APPLIED=C2C 신청 선점 | AWAITING_PAYMENT=입금 확인 대기 중"
                                        + " | SOLD=판매 완료 | CLOSED=신규 참여를 받지 않는 분철의 공석"
                                        + " | CODE_ONLY=참여 코드 보유자에게 배정된 공석)"),
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
                                .optional(),
                            fieldWithPath("flowType")
                                .description("분철 진행 방식 (LEGACY: 즉시 입금 | C2C: 신청→확정→입금 직거래)"),
                            fieldWithPath("paymentDueAt")
                                .description("C2C 일괄 입금 기한. 성사 확정 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("openChatUrl")
                                .description(
                                    "C2C 개최자 오픈채팅 링크. **이 응답에서는** 개최자 본인과 활성 참여자에게만 실린다 — "
                                        + "비로그인·비참여자·취소한 참여자에게는 등록돼 있어도 null "
                                        + "(이 조회가 비로그인에 열려 있어 무조건 실으면 링크가 수집된다). "
                                        + "신청 단계(APPLIED)부터 노출된다. 미등록이거나 LEGACY 면 null. "
                                        + "**서버 전역 규칙이 아니다** — 취소한 참여자도 `GET /v1/participations/{id}` "
                                        + "와 내 참여 목록에서는 링크를 계속 받는다")
                                .optional())
                        .build())));
  }

  @Test
  void C2C_성사_확정() throws Exception {
    given(buncheolService.confirmRecruitment(HOST_ID, 10L))
        .willReturn(
            new BuncheolConfirmResult(
                10L,
                BuncheolStatus.PAYMENT_COLLECTING,
                Instant.parse("2026-08-12T06:00:00Z"),
                5));

    mockMvc
        .perform(post("/v1/buncheols/{id}/confirm", 10L).with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-confirm",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("C2C 개최자 성사 확정")
                        .description(
                            """
                            C2C 분철의 개최자가 성사를 확정한다 (`RECRUITING` → `PAYMENT_COLLECTING`). 신청자 전원이
                            일괄 입금 기한(24시간)과 함께 입금 대기(`AWAITING_PAYMENT`)로 전이되고, 확정 시점 개최자 계좌가
                            분철에 스냅샷되며, 성사 확정·입금 안내 알림이 발송된다. 정원 미달이어도 개최자 재량으로 확정할 수
                            있고(미달 경고는 프론트 담당), 모집 마감 전 조기 확정도 허용된다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철 |
                            | 409 | `BCH-085` (`BUNCHEOL_CONFIRM_NOT_ALLOWED`) | 모집중이 아니거나 신청자가 없음 |
                            | 409 | `USR-030` (`USER_BANK_ACCOUNT_REQUIRED`) | 개최자 계좌 미등록 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .responseSchema(Schema.schema("BuncheolConfirmResponse"))
                        .responseFields(
                            fieldWithPath("buncheolId").description("분철 ID"),
                            fieldWithPath("status").description("전이된 분철 상태 (PAYMENT_COLLECTING)"),
                            fieldWithPath("paymentDueAt").description("일괄 입금 기한 (UTC ISO-8601)"),
                            fieldWithPath("awaitingCount").description("입금 대기로 전이된 참여 수"))
                        .build())));
  }

  @Test
  void C2C_입금_수집_종료() throws Exception {
    mockMvc
        .perform(post("/v1/buncheols/{id}/finalize-collected", 10L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-finalize-collected",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("C2C 입금 수집 종료 (부분 확정)")
                        .description(
                            """
                            입금 기한 경과로 미입금 슬롯이 정리된 뒤, 입금확인된 참여만으로 진행을 확정한다
                            (`PAYMENT_COLLECTING` → `CONFIRMED`). 미입금 활성 참여(입금 대기·보냈어요)가 남아 있거나
                            확정 참여가 없으면 실패한다 — 보냈어요 잔여는 입금확인 또는 반려로 먼저 정리해야 한다.
                            전원 입금확인 시에는 자동으로 진행확정되므로 이 API 는 부분 확정에만 필요하다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철 |
                            | 409 | `BCH-090` (`BUNCHEOL_COLLECT_FINALIZE_NOT_ALLOWED`) | 미입금 활성 참여 잔여·확정 0건·수집중 아님 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
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

                            모집중(`RECRUITING`)·입금 수집중(`PAYMENT_COLLECTING`, C2C)·인원미달 자동취소
                            (`CANCELLED`) 상태에서 취소할 수 있으며, 성공 시 `HOST_CANCELLED` 로 전이된다.
                            진행확정(`CONFIRMED`) 등 그 외 상태이거나 마감 판정과 경합해 이미 전이된 뒤라면 409 로
                            실패한다. 취소된 분철은 목록·상세에서 노출되지 않는다.

                            입금 수집중 분철은 **입금이 확인된 참여가 한 건이라도 있으면 취소할 수 없다**(docs/56 H-13).
                            직거래 구조라 그 돈은 이미 개최자 계좌에 있고 플랫폼이 환불을 강제할 수단이 없어서다 —
                            환불 후 문의 경유로 처리한다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 409 | `BCH-050` (`BUNCHEOL_CANCEL_NOT_ALLOWED`) | 모집중·입금 수집중·인원미달 자동취소 상태가 아니어서 취소 불가 |
                            | 409 | `BCH-093` (`BUNCHEOL_CANCEL_CONFIRMED_PAYMENT_EXISTS`) | 입금이 확인된 참여가 있어 취소 불가 (환불 후 문의 경유) |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));
  }

  @Test
  void 오픈채팅_링크_수정() throws Exception {
    mockMvc
        .perform(
            patch("/v1/buncheols/{id}/open-chat-url", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openChatUrl\": \"https://open.kakao.com/o/gAbCdEf\"}")
                .with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-update-open-chat-url",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("오픈채팅 링크 수정")
                        .description(
                            """
                            개최자가 참여자 소통용 오픈채팅 링크만 수정한다.

                            **전체 수정(`PUT /v1/buncheols/{id}`)과 열려 있는 구간이 다르다.** 전체 수정은 가격·멤버가
                            참여자가 보고 입금한 거래 조건이라 모집중·마감 전으로 묶여 있지만, 링크는 소통 수단이라
                            모집중·입금 수집중·진행확정 어디서든 고칠 수 있다 — 방이 터지거나 잘못 입력한 경우
                            참여자와 연락이 끊기는 쪽이 더 큰 손해다. **마감이 지난 모집중에서도 수정할 수 있다.**

                            취소(`CANCELLED`·`HOST_CANCELLED`)된 분철에서만 막힌다.

                            `openChatUrl` 은 **필수 필드다** — 필드를 생략하거나 `null` 로 보내면 `400 C-001` 이다.
                            **링크 제거는 빈 문자열(`""`)·공백으로만** 표현한다. 전체 수정에서 null 이 "기존 값 유지"
                            인 것과 다르다 — 이 요청은 링크 하나만 담으므로 유지할 다른 값이 없다.

                            **권한**: 해당 분철의 **개최자 본인만** 호출 가능.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `BCH-088` (`BUNCHEOL_OPEN_CHAT_URL_INVALID`) | `https://open.kakao.com/` 으로 시작하지 않거나 값 내부에 공백·제어문자 포함 (전부 공백인 값은 오류가 아니라 링크 제거) |
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `openChatUrl` 누락(필드 생략·`null`) 또는 200자 초과 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 409 | `BCH-094` (`BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE`) | 취소된 분철이라 수정 불가 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestSchema(Schema.schema("OpenChatUrlUpdateRequest"))
                        .requestFields(
                            fieldWithPath("openChatUrl")
                                .description(
                                    "카카오 오픈채팅 링크 (필수, 최대 200자). 빈 문자열·공백이면 링크를 제거한다."))
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
            "유진팬",
            93_000L,
            3_000L,
            ParticipationStatus.CONFIRMED,
            Instant.parse("2026-05-28T00:00:00Z"),
            Instant.parse("2026-05-27T10:00:00Z"),
            null,
            new ManagementDeliveryResponse(
                5001L,
                ShippingMethod.GS25_HALF,
                "GS25 강남역점",
                "유진팬",
                "010-1234-5678",
                "1234567890",
                DeliveryStatus.SHIPPING), null);
    BuncheolManagementParticipantResponse awaiting =
        new BuncheolManagementParticipantResponse(
            602L,
            "레이팬",
            102L,
            "레이",
            "레이팬",
            53_000L,
            3_000L,
            ParticipationStatus.AWAITING_PAYMENT,
            Instant.parse("2026-05-26T00:30:00Z"),
            null,
            null,
            null, null);
    BuncheolManagementParticipantResponse cancelled =
        new BuncheolManagementParticipantResponse(
            603L,
            "가을팬",
            103L,
            "가을",
            "가을팬",
            53_000L,
            3_000L,
            ParticipationStatus.CANCELLED,
            Instant.parse("2026-05-26T00:30:00Z"),
            Instant.parse("2026-05-26T00:10:00Z"),
            new RefundAccountResponse("카카오뱅크", "3333012345678", "가을팬"),
            null,
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
            List.of(confirmed, awaiting),
            List.of(cancelled),
            FlowType.LEGACY,
            null,
            "https://open.kakao.com/o/gAbCdEf");
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
                            - `participants[].depositorName` = **입금자명**(= 참여 시점 참여자의 정산 계좌 예금주 스냅샷). 개최자의 통장 대조 키.
                              **null 일 수 있다** — ⑴ LEGACY 0원(코드) 참여는 대조할 입금이 없어 내리지 않고 ⑵ 참여 계좌 강제 이전에
                              만들어진 0원 참여는 계좌 자체가 없다. **참여자 닉네임으로 폴백해 표시할 것.**
                              C2C 는 0원 슬롯이어도 내려간다 — 대조 단위가 슬롯이 아니라 묶음(같은 사람)이라, 배송비가 첫 슬롯에만
                              붙어 생긴 0원 슬롯의 예금주를 지우면 이체 1건에 대조 키가 갈린다
                            - `participants[].refundAccount` = **평시에는 항상 null.** 계좌번호는 개최자가 실제로 환불해야 하는 건, 즉
                              **취소분 중 입금 흔적(마킹·입금확인)이 있고 0원이 아닌 건**에만 채운다
                            - `participants[].delivery` = 배송 스냅샷. 입금확인(CONFIRMED) 참여에만 생성되며 그 전(AWAITING_PAYMENT)에는 null
                            - `participants[].participationId` = **개최자 입금확인 API(`POST /v1/participations/{id}/confirm`) 의 대상 식별자**
                            - `cancelledParticipants[]` = 취소된 참여 전체. 개최자가 **환불 계좌를 확인**하는 용도다 (C2C 는 대금이
                              개최자 계좌로 직접 입금되는 직거래라 개최자가 환불 주체). 환불이 실제로 필요한지는 개최자가 판단한다.
                              필드 구조는 `participants[]` 와 같고 `status` 는 항상 `CANCELLED`, `delivery` 는 취소 시 정리되어 항상 null.
                              서버가 **입금 흔적이 있는 건만** 계좌를 내리고, 그중 실제 환불이 필요한지는 개최자가 판단한다.
                              `refundAccount` 는 **입금 흔적이 있고 0원이 아닌 건에만** 채워진다(흔적 없는 취소·LEGACY 0원 참여는
                              환불할 돈이 없다).
                              슬롯을 점유하지 않으므로 참여 수·정원 집계에 넣지 않는다

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
                                  "depositorName": "유진팬",
                                  "amount": 93000,
                                  "status": "CONFIRMED",
                                  "dueAt": "2026-05-28T00:00:00Z",
                                  "confirmedAt": "2026-05-27T10:00:00Z",
                                  "refundAccount": null,
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
                              ],
                              "cancelledParticipants": [
                                {
                                  "participationId": 603,
                                  "participantNickname": "가을팬",
                                  "buncheolMemberId": 103,
                                  "memberName": "가을",
                                  "depositorName": "가을팬",
                                  "amount": 53000,
                                  "status": "CANCELLED",
                                  "confirmedAt": "2026-05-26T00:10:00Z",
                                  "refundAccount": {"bank": "카카오뱅크", "account": "3333012345678", "holder": "가을팬"},
                                  "delivery": null
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
                            fieldWithPath("participants[].depositorName")
                                .description(
                                    "입금자명 (= 환불 계좌 예금주). 개최자 통장 대조 키. LEGACY 0원(코드) 참여와 계좌 강제 이전 잔여 행은 null — 닉네임 폴백")
                                .optional(),
                            fieldWithPath("participants[].amount")
                                .description("참여 금액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("participants[].shippingFee")
                                .description(
                                    "amount 에 포함된 배송비(원). 배송비는 묶음당 1회다 — 같은 묶음의 두 번째 슬롯은 0 이지만, 성사 확정 후 추가 모집은 새 묶음이라 같은 사람의 슬롯 두 개가 모두 >0 일 수 있다"),
                            fieldWithPath("participants[].status")
                                .description("참여 상태 (AWAITING_PAYMENT / CONFIRMED)"),
                            fieldWithPath("participants[].dueAt")
                                .description("입금 기한 (UTC ISO-8601)")
                                .optional(),
                            fieldWithPath("participants[].confirmedAt")
                                .description("입금확인 시각. 미확인 시 null")
                                .optional(),
                            fieldWithPath("participants[].refundAccount")
                                .description("환불 계좌. 활성 참여는 항상 null — 입금자명은 depositorName 을 쓴다")
                                .type(JsonFieldType.OBJECT)
                                .optional(),
                            fieldWithPath("participants[].refundAccount.bank")
                                .description("환불 은행명")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("participants[].refundAccount.account")
                                .description("환불 계좌번호")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("participants[].refundAccount.holder")
                                .description("환불 예금주")
                                .type(JsonFieldType.STRING)
                                .optional(),
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
                                .optional(),
                            fieldWithPath("participants[].paymentSentAt")
                                .description(
                                    "C2C '보냈어요' 마킹 시각 — 개최자 통장 대조 우선순위 근거. 마킹 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("cancelledParticipants")
                                .description("취소된 참여 배열. 입금 흔적이 있는 건에만 환불 계좌가 함께 온다. 참여 수·정원 집계에 포함되지 않는다"),
                            fieldWithPath("cancelledParticipants[].participationId")
                                .description("참여 ID"),
                            fieldWithPath("cancelledParticipants[].participantNickname")
                                .description("참여자 닉네임. 조회 불가 시 null")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].buncheolMemberId")
                                .description("분철 멤버 슬롯 ID. 취소분이라 이 슬롯은 다른 참여가 점유했을 수 있다"),
                            fieldWithPath("cancelledParticipants[].memberName")
                                .description("멤버 이름. 멤버가 삭제·이동돼 조회되지 않으면 null")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].depositorName")
                                .description(
                                    "입금자명 (= 환불 계좌 예금주). LEGACY 0원(코드) 참여와 계좌 강제 이전 잔여 행은 null — 닉네임 폴백")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].amount")
                                .description("참여 금액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("cancelledParticipants[].shippingFee")
                                .description("amount 에 포함된 배송비(원)"),
                            fieldWithPath("cancelledParticipants[].status")
                                .description("참여 상태. 이 배열은 항상 CANCELLED"),
                            fieldWithPath("cancelledParticipants[].dueAt")
                                .description("취소 전 입금 기한 (UTC ISO-8601)")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].confirmedAt")
                                .description("입금확인 시각. 입금확인 전에 취소됐으면 null")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].refundAccount")
                                .description(
                                    "환불 계좌. 입금 흔적(paymentSentAt·confirmedAt)이 있고 0원이 아닌 건에만 채워진다 — LEGACY 0원(코드) 참여는 돌려줄 돈이 없다")
                                .type(JsonFieldType.OBJECT)
                                .optional(),
                            fieldWithPath("cancelledParticipants[].refundAccount.bank")
                                .description("환불 은행명")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("cancelledParticipants[].refundAccount.account")
                                .description("환불 계좌번호")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("cancelledParticipants[].refundAccount.holder")
                                .description("환불 예금주")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("cancelledParticipants[].delivery")
                                .description("취소 시 배송 스냅샷이 정리되므로 항상 null")
                                .optional(),
                            fieldWithPath("cancelledParticipants[].paymentSentAt")
                                .description("C2C '보냈어요' 마킹 시각. 마킹 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("flowType")
                                .description("분철 진행 방식 (LEGACY: 즉시 입금 | C2C: 신청→확정→입금 직거래)"),
                            fieldWithPath("paymentDueAt")
                                .description("C2C 일괄 입금 기한. 성사 확정 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("openChatUrl")
                                .description(
                                    "참여자 소통용 오픈채팅 링크. 등록하지 않았으면 null "
                                        + "(개최자가 이 화면에서 PATCH /v1/buncheols/{id}/open-chat-url 로 수정한다)")
                                .optional())
                        .build())));
  }
}
