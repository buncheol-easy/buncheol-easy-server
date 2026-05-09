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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.payment.application.PaymentOrderInfo;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.Collections;
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
