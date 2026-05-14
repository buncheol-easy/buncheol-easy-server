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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.MyParticipationQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
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
                            fieldWithPath("[].bidAmount").description("입찰 금액"),
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
}
