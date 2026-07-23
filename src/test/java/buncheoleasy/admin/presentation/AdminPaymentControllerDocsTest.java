package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminPaymentCommandService;
import buncheoleasy.admin.application.AdminPaymentQueryService;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.admin.dto.response.AdminPaymentRecordResponse;
import buncheoleasy.admin.dto.response.AdminPaymentSummaryResponse;
import buncheoleasy.admin.dto.response.AdminRequestedShippingAddressResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AdminPaymentController 문서화 테스트")
class AdminPaymentControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private AdminPaymentQueryService adminPaymentQueryService;

  @MockitoBean private AdminPaymentCommandService adminPaymentCommandService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void 관리자_결제_목록_조회() throws Exception {
    // given
    AdminPaymentRecordResponse record =
        new AdminPaymentRecordResponse(
            42L,
            "김참여",
            "김실명",
            "장원영",
            13000L,
            AdminPaymentStatus.CONFIRMED,
            ParticipationStatus.CONFIRMED,
            null,
            Instant.parse("2026-07-01T00:30:00Z"),
            Instant.parse("2026-07-01T00:20:00Z"),
            new RefundAccountResponse("국민", "12345678", "홍길동"),
            new ManagementDeliveryResponse(
                7L,
                ShippingMethod.GS25_HALF,
                "역삼점",
                "김참여",
                "01012345678",
                "TRACK-1234",
                DeliveryStatus.SHIPPING),
            new AdminRequestedShippingAddressResponse("GS25_HALF", "역삼점"),
            10L,
            "아이브 포카 분철",
            BuncheolStatus.CONFIRMED,
            "아이브",
            2,
            3L);
    given(adminPaymentQueryService.getPayments(any(), any(), any(), anyInt()))
        .willReturn(new CursorResponse<>(List.of(record), "2026-07-01T00:00:00Z_42", true));

    // when & then
    mockMvc
        .perform(
            get("/v1/admin/payments")
                .header("Authorization", "Bearer {accessToken}")
                .param("status", "CONFIRMED")
                .param("keyword", "아이브")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-payments-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 결제 목록 조회")
                        .description(
                            """
                            전체 분철의 결제(참여)를 최신 참여순 커서 페이지네이션으로 조회한다 (ROLE_ADMIN 전용).
                            paymentStatus 는 파생 상태로, 입금확인 후 분철이 취소된 건은 REFUND_REQUIRED 로 내려간다.
                            keyword 는 분철 제목·그룹명·멤버명·참여자 닉네임·실명 부분 일치 검색이다.""")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .queryParameters(
                            parameterWithName("status")
                                .description(
                                    "파생 상태 필터: AWAITING_CONFIRMATION | CONFIRMED | REFUND_REQUIRED | CANCELLED, 미지정 시 전체")
                                .optional(),
                            parameterWithName("keyword")
                                .description("검색어(분철 제목·그룹명·멤버명·참여자 닉네임·실명 부분 일치)")
                                .optional(),
                            parameterWithName("cursor")
                                .description("다음 페이지 커서(이전 응답의 nextCursor). 첫 페이지는 생략")
                                .optional(),
                            parameterWithName("size")
                                .description("페이지 크기(기본 20, 1~100로 보정)")
                                .optional())
                        .responseSchema(Schema.schema("AdminPaymentListResponse"))
                        .responseFields(
                            fieldWithPath("items[].participationId").description("참여 ID"),
                            fieldWithPath("items[].participantNickname")
                                .description("참여자 닉네임(탈퇴 시 null)")
                                .optional(),
                            fieldWithPath("items[].participantName")
                                .description("참여자 실명(미입력·탈퇴 시 null) — 입금내역 대조용")
                                .optional(),
                            fieldWithPath("items[].memberName")
                                .description("참여한 멤버명")
                                .optional(),
                            fieldWithPath("items[].amount").description("입금 총액(멤버 금액+배송비)"),
                            fieldWithPath("items[].paymentStatus")
                                .description(
                                    "파생 상태: AWAITING_CONFIRMATION | CONFIRMED | REFUND_REQUIRED | CANCELLED"),
                            fieldWithPath("items[].status")
                                .description("참여 원본 상태: AWAITING_PAYMENT | CONFIRMED | CANCELLED"),
                            fieldWithPath("items[].cancelReason")
                                .description("취소 사유: PAYMENT_TIMEOUT | BUNCHEOL_CANCELLED (취소 아니면 null)")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("items[].dueAt").description("입금 만료 시각(UTC)"),
                            fieldWithPath("items[].confirmedAt")
                                .description("입금확인 시각(미확인이면 null)")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("items[].refundAccount.bank").description("환불 은행"),
                            fieldWithPath("items[].refundAccount.account").description("환불 계좌번호"),
                            fieldWithPath("items[].refundAccount.holder").description("환불 예금주"),
                            fieldWithPath("items[].delivery.deliveryId").description("배송 ID"),
                            fieldWithPath("items[].delivery.shippingMethod")
                                .description("배송 방법: GS25_HALF | CU_HALF"),
                            fieldWithPath("items[].delivery.storeName").description("편의점 지점명"),
                            fieldWithPath("items[].delivery.receiverNickname")
                                .description("수령인 닉네임 스냅샷"),
                            fieldWithPath("items[].delivery.receiverPhoneNumber")
                                .description("수령인 연락처 스냅샷"),
                            fieldWithPath("items[].delivery.trackingNumber")
                                .description("운송장 번호(등록 전 null)")
                                .type(JsonFieldType.STRING)
                                .optional(),
                            fieldWithPath("items[].delivery.status")
                                .description(
                                    "배송 상태: SNAPSHOTTED | SHIPPING | DELIVERED | RECEIVED"),
                            fieldWithPath("items[].requestedShippingAddress")
                                .description(
                                    "결제 요청 배송지 — 참여가 선택한 배송지의 현재 원본. 입금확인 전에도 확인용으로 내려가며, 배송지 미지정(레거시 행)이거나 원본 삭제(종료된 참여 한정) 시 null")
                                .optional(),
                            fieldWithPath("items[].requestedShippingAddress.shippingMethod")
                                .description("배송방법 (GS25_HALF | CU_HALF)")
                                .optional(),
                            fieldWithPath("items[].requestedShippingAddress.storeName")
                                .description("편의점 지점명")
                                .optional(),
                            fieldWithPath("items[].buncheolId").description("분철 ID"),
                            fieldWithPath("items[].buncheolTitle").description("분철 제목"),
                            fieldWithPath("items[].buncheolStatus")
                                .description(
                                    "분철 상태: RECRUITING | CONFIRMED | CANCELLED | HOST_CANCELLED"),
                            fieldWithPath("items[].groupName").description("그룹명"),
                            fieldWithPath("items[].minHeadcount").description("분철 진행 최소 인원"),
                            fieldWithPath("items[].confirmedCount")
                                .description("해당 분철의 입금확인 인원 수"),
                            fieldWithPath("nextCursor")
                                .description("다음 페이지 커서(없으면 null)")
                                .optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"))
                        .build())));
  }

  @Test
  void 관리자_결제_통계_조회() throws Exception {
    // given
    given(adminPaymentQueryService.getSummary())
        .willReturn(new AdminPaymentSummaryResponse(2, 5, 1, 3, 11, 33000));

    // when & then
    mockMvc
        .perform(
            get("/v1/admin/payments/summary").header("Authorization", "Bearer {accessToken}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-payments-summary",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 결제 통계 조회")
                        .description("결제 대시보드 상단 통계. 파생 상태별 건수와 확인 대기 금액 합계 (ROLE_ADMIN 전용).")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("AdminPaymentSummaryResponse"))
                        .responseFields(
                            fieldWithPath("awaitingCount").description("입금확인 대기 건수"),
                            fieldWithPath("confirmedCount").description("입금확인 완료 건수"),
                            fieldWithPath("refundRequiredCount").description("환불 필요 건수"),
                            fieldWithPath("cancelledCount").description("취소 건수(환불 불필요)"),
                            fieldWithPath("totalCount").description("전체 건수"),
                            fieldWithPath("awaitingAmount").description("확인 대기 금액 합계(배송비 포함)"))
                        .build())));
  }

  @Test
  void 관리자_입금확인_벌크_처리() throws Exception {
    // given
    given(adminPaymentCommandService.confirmPayments(anyList()))
        .willReturn(
            new AdminBulkResultResponse(
                List.of(1L, 2L),
                List.of(
                    new AdminBulkResultResponse.Failure(
                        3L, "BCH-067", "현재 참여 상태에서는 요청한 작업을 수행할 수 없습니다."))));

    // when & then
    mockMvc
        .perform(
            post("/v1/admin/payments/confirm")
                .header("Authorization", "Bearer {accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participationIds\": [1, 2, 3]}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-payments-confirm",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 입금확인 벌크 처리")
                        .description(
                            """
                            묶음 입금된 여러 참여를 한 번에 입금확인한다 (ROLE_ADMIN 전용). 개최자 소유권 검증 없이 전체 분철에 적용된다.
                            건별로 독립 처리하므로 일부 실패(이미 확정/취소 등)가 나머지 성공을 되돌리지 않으며, 실패 건은 사유와 함께 내려간다.""")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("AdminPaymentConfirmRequest"))
                        .requestFields(
                            fieldWithPath("participationIds").description("입금확인할 참여 ID 목록"))
                        .responseSchema(Schema.schema("AdminBulkResultResponse"))
                        .responseFields(
                            fieldWithPath("succeededIds").description("입금확인에 성공한 참여 ID 목록"),
                            fieldWithPath("failures[].id").description("실패한 참여 ID"),
                            fieldWithPath("failures[].code").description("실패 사유 에러 코드"),
                            fieldWithPath("failures[].message").description("실패 사유 메시지"))
                        .build())));
  }
}
