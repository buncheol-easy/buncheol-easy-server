package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 빈 교체 목록을 BuncheolControllerDocsTest 와 같게 두어 테스트 컨텍스트를 함께 쓴다.
@DisplayName("StaffBuncheolController 문서화 테스트")
class StaffBuncheolControllerDocsTest extends DocsTestSupport {

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;

  private String holdRequestJson() {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant opensAt = deadline.minus(1, ChronoUnit.DAYS);
    return """
        {
          "groupId": 100,
          "title": "몬스타엑스 디지팩 분철",
          "description": "1회차",
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          "opensAt": "%s",
          "minHeadcount": 1,
          "gs25ShippingFee": 3000,
          "cuShippingFee": 3000,
          "thumbnailIndex": 0,
          "buncheolMembers": [
            {"memberId": 200, "price": 15000},
            {"memberId": 201, "price": 15000},
            {"memberId": 202, "price": 0, "accessType": "CODE_ONLY"}
          ]
        }
        """
        .formatted(deadline, opensAt);
  }

  @Test
  void 운영진_분철_개최() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, holdRequestJson().getBytes());
    MockMultipartFile imagePart =
        new MockMultipartFile(
            "images", "album-cover.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

    given(buncheolService.holdStaffBuncheol(any(), any(), any())).willReturn(10L);

    mockMvc
        .perform(
            multipart("/v1/staff/buncheols").file(requestPart).file(imagePart).with(userAuth()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.buncheolId").value(10))
        .andDo(
            document(
                "staff-buncheols-hold",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("운영진 분철 개최")
                        .description(
                            """
                            운영진(`can_host`) 계정만 쓸 수 있다. 회원 토큰으로 부르고, 운영진이 아니면 `403 USR-031` 이다.
                            언제나 운영진 분철(`flowType = LEGACY`)을 만든다. 회원 개최(`POST /v1/buncheols`)는 이 API 와 무관하게 그대로다.

                            예약 오픈 시각(`opensAt`)을 넣으면 그 시각 전에는 코드 없는 참여를 받지 않는다(`409 BCH-128`).
                            코드 전용 자리(`accessType: "CODE_ONLY"`)는 오픈 전에도 유효한 코드로 참여할 수 있다.
                            분철과 오픈 시각은 한 트랜잭션에서 함께 저장된다 — 오픈 시각 없이 먼저 열린 분철이 보이는 순간은 없다.
                            오픈 시각을 고치는 API 는 없다.

                            multipart/form-data 요청. 파트 구성은 회원 개최와 같다.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "groupId": Long,                // 그룹 ID
                              "title": String,                // 1~64자
                              "description": String?,         // 선택, 700자 이하
                              "purchaseSite": String,         // 1~200자
                              "deadline": Instant,            // 미래 정각 (UTC ISO-8601, 예: 2026-10-21T11:00:00Z)
                              "opensAt": Instant?,            // 선택, 예약 오픈 시각 (UTC ISO-8601). 없으면 등록하자마자 열린다. 지금 이후·마감 전, 초 아래는 버린다
                              "minHeadcount": Integer,        // 1 이상, buncheolMembers 개수 이하
                              "gs25ShippingFee": Integer?,    // 0 이상, gs25/cu 중 최소 1개 필수
                              "cuShippingFee": Integer?,      // 0 이상, gs25/cu 중 최소 1개 필수
                              "openChatUrl": String?,         // 선택, https://open.kakao.com/ 시작·200자 이하
                              "thumbnailIndex": Integer,      // 필수, 대표사진으로 쓸 images 파트 내 인덱스(0-base)
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "price": Long,              // 0 이상, 100원 단위
                                  "accessType": String?       // 선택 ("OPEN"|"CODE_ONLY") — 생략 시 OPEN. CODE_ONLY 도 다른 자리와 같은 가격을 걸 수 있다(코드 참여는 가격과 관계없이 0원)
                                }
                              ]
                            }
                            ```
                            회원 개최 요청의 `flowType` 칸은 없다(보내도 무시된다).

                            **images 파트** (**필수**): 이미지 파일 목록, **최소 1장 ~ 최대 5장**. 규칙은 회원 개최와 같다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `request` 검증 실패 또는 `images` 파트 누락 (회원 개최와 같은 검증) |
                            | 400 | `BCH-129` (`BUNCHEOL_OPENS_AT_INVALID`) | `opensAt` 이 지금 이전·지금과 같음, 또는 마감과 같거나 뒤 — 분철을 만들지 않는다 |
                            | 400 | `BCH-027` (`BUNCHEOL_MEMBER_PRICE_INVALID`) | `price` 가 100원 단위가 아님 |
                            | 400 | `BCH-045` (`BUNCHEOL_IMAGE_REQUIRED`) | 이미지가 0장 |
                            | 400 | `BCH-040` (`BUNCHEOL_IMAGE_LIMIT_EXCEEDED`) | 이미지가 5장 초과 |
                            | 400 | `BCH-047` (`BUNCHEOL_THUMBNAIL_INDEX_INVALID`) | `thumbnailIndex` 가 images 파트 범위를 벗어남 |
                            | 400 | `BCH-088` (`BUNCHEOL_OPEN_CHAT_URL_INVALID`) | `openChatUrl` 형식 위반 |
                            | 400 | `BCH-123` (`BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS`) | `minHeadcount` 가 `buncheolMembers` 개수보다 큼 |
                            | 400 | `FILE-001` (`FILE_NAME_INVALID`) | 이미지 파일명이 없거나 확장자가 없음 |
                            | 400 | `FILE-002` (`FILE_EXTENSION_INVALID`) | `.jpg` `.jpeg` `.png` `.webp` 밖의 이미지가 있음 |
                            | 403 | `USR-031` (`USER_CANNOT_HOST`) | 운영진(`can_host`)이 아님 — 분철을 만들지 않는다 |
                            | 409 | `USR-025` (`USER_BANK_ACCOUNT_NOT_REGISTERED`) | 정산 계좌 미등록 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .responseFields(
                            fieldWithPath("buncheolId")
                                .type(JsonFieldType.NUMBER)
                                .description("생성된 분철 id (회원 개최 응답과 같다)"))
                        .responseSchema(Schema.schema("HoldBuncheolResponse"))
                        .build())));
  }
}
