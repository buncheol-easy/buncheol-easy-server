package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminShippingFeePaybackCommandService;
import buncheoleasy.admin.application.AdminShippingFeePaybackQueryService;
import buncheoleasy.admin.dto.request.AdminShippingFeePaybackActionRequest;
import buncheoleasy.admin.dto.response.AdminShippingFeePaybackResponse;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.global.page.CursorResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminShippingFeePaybackController 문서화 테스트")
class AdminShippingFeePaybackControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminShippingFeePaybackQueryService adminShippingFeePaybackQueryService;

  @MockitoBean private AdminShippingFeePaybackCommandService adminShippingFeePaybackCommandService;

  @Test
  void 관리자_배송비_환급_신청_목록_조회() throws Exception {
    // given
    AdminShippingFeePaybackResponse item =
        new AdminShippingFeePaybackResponse(
            500L,
            "참여자닉",
            "김실명",
            10L,
            "뉴진스 1집 분철",
            "민지",
            3_000L,
            new RefundAccountResponse("국민은행", "12345678", "홍길동"),
            "https://x.com/fan/status/1234567890",
            PaybackStatus.REQUESTED,
            Instant.parse("2026-07-16T09:00:00Z"),
            null,
            null);
    given(
            adminShippingFeePaybackQueryService.getPaybacks(
                eq(PaybackStatus.REQUESTED), any(), anyInt()))
        .willReturn(new CursorResponse<>(List.of(item), "2026-07-16T09:00:00Z_500", true));

    // when & then
    mockMvc
        .perform(
            get("/v1/admin/shipping-fee-paybacks")
                .with(adminAuth())
                .queryParam("status", "REQUESTED")
                .queryParam("size", "20"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-shipping-fee-paybacks-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 배송비 환급 신청 목록 조회")
                        .description(
                            """
                            오픈 이벤트 배송비 환급(배송비 돌려받기) 신청 이력이 있는 참여 목록을 신청 최신순으로 조회한다 (ROLE_ADMIN 전용).
                            운영진은 후기 트윗을 확인한 뒤 건별 PATCH 로 입금 완료/반려 처리한다.""")
                        .requestHeaders(adminAuthorizationHeader())
                        .queryParameters(
                            parameterWithName("status")
                                .description(
                                    "저장 상태 필터 (REQUESTED | COMPLETED | REJECTED). 생략 시 신청 이력 전체")
                                .optional(),
                            parameterWithName("cursor")
                                .description("이전 응답의 nextCursor. 첫 페이지는 생략")
                                .optional(),
                            parameterWithName("size").description("페이지 크기 (기본 20, 최대 100)").optional())
                        .responseSchema(Schema.schema("AdminShippingFeePaybackListResponse"))
                        .responseFields(
                            fieldWithPath("items[].participationId").description("참여 ID (검수 처리 시 사용)"),
                            fieldWithPath("items[].participantNickname")
                                .description("신청자 닉네임. 탈퇴 유저면 null")
                                .optional(),
                            fieldWithPath("items[].participantName")
                                .description("신청자 실명. 미등록이면 null")
                                .optional(),
                            fieldWithPath("items[].buncheolId").description("분철 ID"),
                            fieldWithPath("items[].buncheolTitle").description("분철 제목"),
                            fieldWithPath("items[].memberName")
                                .description("참여한 멤버 이름")
                                .optional(),
                            fieldWithPath("items[].paybackAmount")
                                .description("환급액 (신청 시점 배송비 스냅샷, 원)"),
                            fieldWithPath("items[].refundAccount").description("환급 입금할 환불계좌"),
                            fieldWithPath("items[].refundAccount.bank").description("은행명"),
                            fieldWithPath("items[].refundAccount.account").description("계좌번호"),
                            fieldWithPath("items[].refundAccount.holder").description("예금주"),
                            fieldWithPath("items[].tweetUrl").description("후기 트윗 URL"),
                            fieldWithPath("items[].status")
                                .description("저장 상태 (REQUESTED | COMPLETED | REJECTED)"),
                            fieldWithPath("items[].requestedAt").description("신청(재신청 포함) 시각"),
                            fieldWithPath("items[].completedAt")
                                .description("입금 완료 시각. 완료 전에는 null")
                                .optional(),
                            fieldWithPath("items[].rejectReason")
                                .description("반려 사유. REJECTED 외에는 null")
                                .optional(),
                            fieldWithPath("nextCursor")
                                .description(
                                    "다음 페이지 커서 (`<requestedAt ISO-8601>_<id>`). hasNext=false 면 null")
                                .optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"))
                        .build())));
  }

  @Test
  void 관리자_배송비_환급_검수_처리() throws Exception {
    // when & then
    mockMvc
        .perform(
            patch("/v1/admin/shipping-fee-paybacks/{participationId}", 500L)
                .with(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"REJECT\", \"rejectReason\": \"비공개 계정이라 후기를 확인할 수 없어요.\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "admin-shipping-fee-paybacks-process",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 배송비 환급 검수 처리")
                        .description(
                            """
                            환급 신청 1건을 검수 처리한다 (ROLE_ADMIN 전용).

                            - `COMPLETE`: 승인·입금 완료를 한 번에 처리 (REQUESTED → COMPLETED)
                            - `REJECT`: 반려 + 사유 필수 (REQUESTED → REJECTED, 유저는 재신청 가능)

                            | 상태 | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `BCH-081` (`PAYBACK_REJECT_REASON_REQUIRED`) | REJECT 인데 사유 누락 |
                            | 404 | `BCH-068` (`PARTICIPATION_NOT_FOUND`) | 참여 없음 |
                            | 409 | `BCH-077` (`PAYBACK_STATE_TRANSITION_INVALID`) | 현재 상태에서 불가한 전이 |
                            """)
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestSchema(Schema.schema("AdminShippingFeePaybackActionRequest"))
                        .requestFields(
                            fieldWithPath("action").description("처리 액션 (COMPLETE | REJECT)"),
                            fieldWithPath("rejectReason")
                                .description("반려 사유 (REJECT 시 필수, 200자 이내)")
                                .optional())
                        .build())));

    ArgumentCaptor<AdminShippingFeePaybackActionRequest> captor =
        ArgumentCaptor.forClass(AdminShippingFeePaybackActionRequest.class);
    then(adminShippingFeePaybackCommandService).should().process(eq(500L), captor.capture());
    assertThat(captor.getValue().action())
        .isEqualTo(AdminShippingFeePaybackActionRequest.Action.REJECT);
  }
}
