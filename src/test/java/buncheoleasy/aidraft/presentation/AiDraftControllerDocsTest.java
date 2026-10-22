package buncheoleasy.aidraft.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.aidraft.application.AiDraftService;
import buncheoleasy.aidraft.dto.request.CreateAiDraftRequest;
import buncheoleasy.aidraft.dto.response.AiDraftQuotaResponse;
import buncheoleasy.aidraft.dto.response.AiDraftResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AiDraftController 문서화 테스트")
class AiDraftControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AiDraftService aiDraftService;

  @Test
  void AI_개최글_초안_만들기() throws Exception {
    given(aiDraftService.createDraft(eq(USER_ID), any(CreateAiDraftRequest.class)))
        .willReturn(
            new AiDraftResponse(
                "투모로우바이투게더 미니 7집 분철해요",
                "케이타운포유 예약판매 앨범을 멤버별로 나눠요.\n구성: 포토북 1종, 포토카드 1장(랜덤)",
                "케이타운포유",
                "https://www.ktown4u.com/iteminfo?goods_no=150001",
                new AiDraftResponse.Group(12L, "투모로우바이투게더"),
                List.of("투모로우바이투게더 (TXT)", "TOMORROW X TOGETHER", "TXT"),
                14100L,
                "KRW",
                "2026-11-03",
                List.of("/9j/4AAQSkZJRgABAQ...", "iVBORw0KGgoAAAANSUhEUg..."),
                0,
                9));

    mockMvc
        .perform(
            post("/v1/ai/drafts")
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "url": "https://www.ktown4u.com/iteminfo?goods_no=150001"
                    }"""))
        .andExpect(status().isOk())
        .andDo(
            document(
                "ai-drafts-create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("AiDraft")
                        .summary("AI 개최글 초안 만들기")
                        .description(
                            """
                            판매 페이지 주소(또는 붙여 넣은 상품 설명)로 AI 엔진에 개최글 초안을 받아 **개최 폼에 미리 채울 모양**으로
                            돌려준다. 요청이 엔진 응답을 기다리는 동기 처리다(보통 7초, 최대 25초 대기). 마감·멤버·가격은 주지 않는다.

                            개최 자격이 없으면 개최 요청(`POST /v1/buncheols`)과 같은 오류를 준다
                            (`GET /v1/buncheols/hosting-eligibility` 의 reason 표 참고 — 403 `USR-018`·409 `USR-032`·403 `USR-033`·409 `BCH-089`·409 `USR-025`).

                            **하루 횟수**: 1인 하루 10회(한국 시간 자정 초기화). 판매 페이지를 읽지 못한 실패처럼 AI 까지 가지 않은 실패는 횟수를
                            돌려준다. 같은 실패를 끝없이 반복하지 못하게 따로 숨은 시도 상한이 있다.

                            **오류**
                            | 코드 | 상태 | 언제 | 하루 횟수 |
                            |------|------|------|-----------|
                            | `AI-001` | 400 | 입력 위반(둘 다·둘 다 없음·http/https 아님·주소 2000자·글 5000자 초과) · 엔진이 거절한 주소(내부망 등) | 돌려줌 |
                            | `AI-002` | 422 | 판매 페이지를 읽지 못함 — 화면은 붙여 넣기 안내 | 돌려줌 |
                            | `AI-003` | 429 | 오늘 횟수를 다 씀 | — |
                            | `AI-004` | 502 | AI 가 글을 쓰지 못함 · 엔진 응답 시간 초과 | 안 돌려줌(AI 를 이미 불렀을 수 있음) |
                            | `AI-005` | 503 | 지금 못 씀(서버 동시 처리 상한·엔진 바쁨·엔진 연결 실패·횟수 저장소 장애) | 돌려줌 |
                            | `AI-006` | 409 | 같은 사람이 이미 작성 중 | — |
                            | `AI-007` | 409 | 기능 꺼짐 | — |
                            """)
                        .requestSchema(Schema.schema("CreateAiDraftRequest"))
                        .responseSchema(Schema.schema("AiDraftResponse"))
                        .requestHeaders(userAuthorizationHeader())
                        .requestFields(
                            fieldWithPath("url")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("판매 페이지 주소 (http/https, 2000자 이하). rawText 와 둘 중 정확히 하나"),
                            fieldWithPath("rawText")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description(
                                    "붙여 넣은 상품 설명 (5000자 이하). 판매 페이지를 읽지 못할 때(AI-002) 쓴다. url 과 둘 중 정확히 하나"))
                        .responseFields(
                            fieldWithPath("title").description("제목 (64자 이하로 맞춤)"),
                            fieldWithPath("description").description("본문 (700자 이하로 맞춤)"),
                            fieldWithPath("purchaseSite")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("구매처 이름 (20자 이하). 엔진이 모르는 판매처면 null"),
                            fieldWithPath("purchaseUrl")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("판매 페이지 주소. 붙여 넣은 글이면 null"),
                            fieldWithPath("group")
                                .type(JsonFieldType.OBJECT)
                                .optional()
                                .description("확정한 그룹. 확정하지 못했으면 null — groupKeywords 로 그룹 검색 칸을 채운다"),
                            fieldWithPath("group.id").type(JsonFieldType.NUMBER).optional().description("그룹 ID"),
                            fieldWithPath("group.name").type(JsonFieldType.STRING).optional().description("그룹 이름"),
                            fieldWithPath("groupKeywords")
                                .description("그룹 찾기에 쓴 검색어(시도 순서). 결과가 정확히 1개인 첫 검색어에서 확정한다"),
                            fieldWithPath("listPrice")
                                .type(JsonFieldType.NUMBER)
                                .optional()
                                .description("정가(원). 원화로 확인된 값만, 없으면 null"),
                            fieldWithPath("priceCurrency")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("정가 통화. 정가가 없으면 null"),
                            fieldWithPath("releaseDate")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("발매일 (yyyy-MM-dd). 모르면 null"),
                            fieldWithPath("images")
                                .description(
                                    "사진 후보 0~2장 — base64(데이터 URL 접두어 없음). 형식(jpeg·png 등)은 따로 주지 않는다"),
                            fieldWithPath("thumbnailIndex").description("대표사진 순번 (images 기준)"),
                            fieldWithPath("remainingToday").description("이 요청 뒤 오늘 남은 AI 작성 횟수"))
                        .build())));
  }

  @Test
  void AI_개최글_남은_횟수_조회() throws Exception {
    given(aiDraftService.getQuota(USER_ID)).willReturn(new AiDraftQuotaResponse(true, 10, 9));

    mockMvc
        .perform(get("/v1/ai/drafts/quota").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "ai-drafts-quota",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("AiDraft")
                        .summary("AI 개최글 남은 횟수 조회")
                        .description(
                            """
                            오늘(한국 시간 자정 초기화) 남은 AI 작성 횟수. 기능이 꺼져 있어도 **200 으로 `enabled=false`** 를 준다 —
                            화면이 AI 칸을 숨기는 신호다. 횟수 저장소 장애면 503 `AI-005`.""")
                        .responseSchema(Schema.schema("AiDraftQuotaResponse"))
                        .requestHeaders(userAuthorizationHeader())
                        .responseFields(
                            fieldWithPath("enabled").description("기능을 쓸 수 있는지"),
                            fieldWithPath("dailyLimit").description("1인 하루 한도"),
                            fieldWithPath("remainingToday").description("오늘 남은 횟수. 꺼져 있으면 0"))
                        .build())));
  }
}
