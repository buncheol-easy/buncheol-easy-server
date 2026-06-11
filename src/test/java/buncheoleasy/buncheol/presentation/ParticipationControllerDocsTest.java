package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.HostPaymentService;
import buncheoleasy.buncheol.application.MyParticipationQueryService;
import buncheoleasy.buncheol.application.ParticipationPaymentQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ReportPaymentRequest;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse;
import buncheoleasy.payment.application.PaymentOrderInfo;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
@DisplayName("Participation 관련 컨트롤러 문서화 테스트")
class ParticipationControllerDocsTest {

  private static final Long PARTICIPANT_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BuncheolCheckoutService buncheolCheckoutService;

  @MockitoBean private HostPaymentService hostPaymentService;

  @MockitoBean private ParticipationPaymentQueryService participationPaymentQueryService;

  @MockitoBean private MyParticipationQueryService myParticipationQueryService;

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
              new UsernamePasswordAuthenticationToken(
                  PARTICIPANT_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Test
  void 분철_참여() throws Exception {
    // given
    Participation participation = Mockito.mock(Participation.class);
    given(participation.getId()).willReturn(500L);
    given(participation.getStatus()).willReturn(ParticipationStatus.ACTIVE_BID);
    given(participation.getBidAmount()).willReturn(60000L);
    given(buncheolCheckoutService.participate(eq(10L), eq(PARTICIPANT_ID), any()))
        .willReturn(participation);

    // when & then
    mockMvc
        .perform(
            post("/v1/buncheols/{buncheolId}/participations", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"buncheolMemberId\":200,\"shippingAddressId\":1,\"bidAmount\":60000}"))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "participations-participate",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("분철 참여 신청")
                        .description("참여 즉시 ACTIVE_BID 상태로 등록되며, 결제는 마감 후 낙찰자에 한해 진행한다.")
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("ParticipateRequest"))
                        .requestFields(
                            fieldWithPath("buncheolMemberId").description("입찰할 분철 멤버 ID"),
                            fieldWithPath("shippingAddressId").description("수령지 ID"),
                            fieldWithPath("bidAmount").description("입찰 금액 (양수)"))
                        .responseSchema(Schema.schema("ParticipationCheckoutResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("참여 ID"),
                            fieldWithPath("participationStatus")
                                .description("참여 상태 (ACTIVE_BID 등)"),
                            fieldWithPath("bidAmount").description("입찰 금액"))
                        .build())));
  }

  @Test
  void 내_참여_목록_조회() throws Exception {
    Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
    Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
    MyParticipationResponse response =
        new MyParticipationResponse(
            500L,
            10L,
            "뉴진스 1집 분철",
            5,
            "민지",
            50_000L,
            3_000L,
            53_000L,
            ParticipationStatus.AWAITING_PAYMENT,
            BuncheolStatus.CLOSED,
            deadline,
            dueAt,
            1);
    given(myParticipationQueryService.getMyParticipations(PARTICIPANT_ID))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/participations/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participations-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("내가 참여한 분철 목록 조회")
                        .description("마이페이지에서 사용자가 참여한 분철 목록을 최신 참여 순으로 조회한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("MyParticipationListResponse"))
                        .responseFields(
                            fieldWithPath("[].participationId").description("참여 ID"),
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].buncheolTitle").description("분철 제목"),
                            fieldWithPath("[].buncheolMemberCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].memberName").description("내가 참여한 멤버 이름"),
                            fieldWithPath("[].bidAmount").description("입찰 금액 (제시가)"),
                            fieldWithPath("[].shippingFee").description("배송비 (참여자가 선택한 배송수단 기준)"),
                            fieldWithPath("[].paymentAmount").description("낙찰자 결제 금액 (제시가 + 배송비)"),
                            fieldWithPath("[].participationStatus")
                                .description(
                                    "내 참여 상태 (ACTIVE_BID | AWAITING_PAYMENT | CONFIRMED | CANCELLED | FAILED)"),
                            fieldWithPath("[].buncheolStatus")
                                .description("분철 진행 상태 (RECRUITING | CLOSED | ...)"),
                            fieldWithPath("[].buncheolDeadline").description("분철 모집 마감일"),
                            fieldWithPath("[].paymentDueAt")
                                .description("낙찰자 결제 마감 시각. AWAITING_PAYMENT 가 아니면 null")
                                .optional(),
                            fieldWithPath("[].closedRank")
                                .description("마감 후 입찰 순위 (1위부터). 마감 전이면 null")
                                .optional())
                        .build())));
  }

  @Test
  void 낙찰자_mock_결제_확정() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/payment", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-confirm-mock",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("낙찰자 결제 확정 (mock)")
                        .description(
                            "분철 낙찰자가 결제를 확정해 참여를 CONFIRMED 로 전환한다. 결제 수단 확정 전까지는 호출 즉시 결제된 것으로 처리한다. (추후 실제 PG 로 교체)")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 분철_참여_취소() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/v1/participations/{participationId}", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("분철 참여 취소")
                        .description("참여자 본인이 분철 참여를 취소한다. ACTIVE_BID 상태에서만 취소가 가능하다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 결제_체크아웃_시작() throws Exception {
    // given
    given(buncheolCheckoutService.startPaymentCheckout(PARTICIPANT_ID, 500L))
        .willReturn(
            new PaymentOrderInfo(
                "ck_test_xxx",
                "ord-20260507-001",
                "뉴진스 1집 분철 - 민지",
                60000L,
                "https://example.com/v1/payments/success",
                "https://example.com/v1/payments/fail"));

    // when & then
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/payment/checkout", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "participations-start-checkout",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("결제 체크아웃 시작")
                        .description("낙찰자가 결제를 시작할 수 있도록 토스페이먼츠 주문 정보를 생성한다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("CreatePaymentOrderResponse"))
                        .responseFields(
                            fieldWithPath("clientKey").description("토스페이먼츠 클라이언트 키"),
                            fieldWithPath("paymentOrderId").description("주문 ID"),
                            fieldWithPath("paymentOrderName").description("주문명"),
                            fieldWithPath("amount").description("결제 금액"),
                            fieldWithPath("successUrl").description("결제 성공 시 리다이렉트 URL"),
                            fieldWithPath("failUrl").description("결제 실패 시 리다이렉트 URL"))
                        .build())));
  }

  @Test
  void 구매자_입금완료_신고() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/payment/report", 500L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddressId\": 200}")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-report",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("구매자 입금 완료 신고")
                        .description(
                            "낙찰자가 개최자 계좌로 송금 후 '입금했어요'를 누른다. AWAITING_PAYMENT → PAYMENT_REPORTED 로"
                                + " 전이하며, 입금 기한 내에만 가능하다. 신고 시 최종 배송지 ID를 본문으로 받는다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("ReportPaymentRequest"))
                        .requestFields(
                            fieldWithPath("shippingAddressId").description("낙찰자가 선택한 최종 배송지 ID"))
                        .build())));
  }

  @Test
  void 개최자_입금확인() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/payment/confirm", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-confirm",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("개최자 수동 입금 확인")
                        .description(
                            "개최자가 실제 입금을 확인해 PAYMENT_REPORTED → CONFIRMED 로 전환한다. 확정 시 배송 스냅샷이"
                                + " 생성된다. 개최자 본인만 호출할 수 있다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 개최자_미입금_낙찰자_만료() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/payment/expire", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-expire",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("개최자 미입금 낙찰자 만료 및 차순위 승계")
                        .description(
                            "개최자가 입금 기한이 지난 AWAITING_PAYMENT 낙찰자를 FAILED 처리하고, 같은 멤버 슬롯의 차순위"
                                + " 후보(ACTIVE_BID)를 입금대기로 승계한다. 승계 결과는 분철 관리 화면 재조회로 확인한다. 개최자 본인만"
                                + " 호출할 수 있으며, PAYMENT_REPORTED/CONFIRMED 는 만료 대상이 아니고 입금 기한 전에는 거부된다(BCH-074).")
                        .pathParameters(
                            parameterWithName("participationId").description("만료시킬 낙찰자 참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 낙찰자_결제_상세_조회() throws Exception {
    ParticipationPaymentDetailResponse detail =
        new ParticipationPaymentDetailResponse(
            500L,
            ParticipationStatus.AWAITING_PAYMENT,
            50_000L,
            3_000L,
            53_000L,
            Instant.parse("2026-06-02T12:00:00Z"),
            new ParticipationPaymentDetailResponse.HostAccountResponse(
                "국민은행", "12345678", "홍길동"));
    given(participationPaymentQueryService.getPaymentDetail(PARTICIPANT_ID, 500L))
        .willReturn(detail);

    mockMvc
        .perform(
            get("/v1/participations/{participationId}/payment", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participations-payment-detail",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("낙찰자 결제 상세 조회")
                        .description(
                            "낙찰자 본인이 결제 금액·기한·개최자 계좌를 조회한다. 개최자 계좌는 AWAITING_PAYMENT/PAYMENT_REPORTED"
                                + " 단계에서만 노출되며, 그 외 상태에서는 hostAccount 가 null 이다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("ParticipationPaymentDetailResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("참여 ID"),
                            fieldWithPath("paymentStatus")
                                .description(
                                    "결제(참여) 상태 (AWAITING_PAYMENT | PAYMENT_REPORTED | CONFIRMED 등)"),
                            fieldWithPath("bidAmount").description("낙찰가 (제시가)"),
                            fieldWithPath("shippingFee").description("배송비"),
                            fieldWithPath("totalAmount").description("총 결제 금액 (낙찰가 + 배송비)"),
                            fieldWithPath("paymentDueAt").description("입금 기한").optional(),
                            fieldWithPath("hostAccount.bankName")
                                .description("개최자 은행명 (노출 조건 미충족 시 hostAccount 전체가 null)")
                                .optional(),
                            fieldWithPath("hostAccount.accountNumber")
                                .description("개최자 계좌번호")
                                .optional(),
                            fieldWithPath("hostAccount.accountHolder")
                                .description("개최자 예금주")
                                .optional())
                        .build())));
  }
}
