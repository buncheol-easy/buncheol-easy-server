package buncheoleasy.buncheol.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.MyParticipationQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("ParticipationController 테스트")
class ParticipationControllerTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 50L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BuncheolCheckoutService buncheolCheckoutService;

  @MockitoBean private MyParticipationQueryService myParticipationQueryService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

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

  private static final String VALID_REQUEST_BODY =
      """
      {
        "buncheolMemberId": 10,
        "shippingAddressId": 200,
        "bidAmount": 30000
      }
      """;

  @Nested
  @DisplayName("분철 참여 신청 API 테스트")
  class ParticipateTest {

    @Test
    void 참여_신청에_성공하면_201을_반환한다() throws Exception {
      Participation participation =
          Participation.create(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setFieldValue(participation, "id", PARTICIPATION_ID);

      given(
              buncheolCheckoutService.participate(
                  eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class)))
          .willReturn(participation);

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.participationId").value(PARTICIPATION_ID))
          .andExpect(jsonPath("$.participationStatus").value("ACTIVE_BID"))
          .andExpect(jsonPath("$.bidAmount").value(30_000));
    }

    @Test
    void 모집중이_아닌_분철이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheolCheckoutService)
          .participate(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.BUNCHEOL_NOT_RECRUITING.getCode())));
    }

    @Test
    void 호스트가_참여하면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE))
          .given(buncheolCheckoutService)
          .participate(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE.getCode())));
    }

    @Test
    void 이미_활성_참여가_있으면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS))
          .given(buncheolCheckoutService)
          .participate(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_ALREADY_EXISTS.getCode())));
    }

    @Test
    void 필수_필드가_누락되면_400을_반환한다() throws Exception {
      String invalidJson =
          """
          {
            "buncheolMemberId": 10
          }
          """;

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidJson)
                  .with(mockAuth()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("낙찰자 결제 주문 생성 API 테스트")
  class StartPaymentCheckoutTest {

    @Test
    void 결제_주문_생성에_성공하면_201을_반환한다() throws Exception {
      PaymentOrderInfo paymentOrderInfo =
          new PaymentOrderInfo(
              "clientKey", "order_123", "분철 낙찰자 결제", 25_000L, "http://success", "http://fail");

      given(buncheolCheckoutService.startPaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID)))
          .willReturn(paymentOrderInfo);

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/payment/checkout", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.clientKey").value("clientKey"))
          .andExpect(jsonPath("$.paymentOrderId").value("order_123"))
          .andExpect(jsonPath("$.amount").value(25_000));
    }

    @Test
    void 참여가_존재하지_않으면_404를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND))
          .given(buncheolCheckoutService)
          .startPaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/payment/checkout", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PARTICIPATION_NOT_FOUND.getCode())));
    }

    @Test
    void 참여자가_아니면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION))
          .given(buncheolCheckoutService)
          .startPaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/payment/checkout", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_NO_PERMISSION.getCode())));
    }

    @Test
    void 결제가_허용되지_않는_상태이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED))
          .given(buncheolCheckoutService)
          .startPaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/payment/checkout", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED.getCode())));
    }
  }

  @Nested
  @DisplayName("내 참여 목록 조회 API 테스트")
  class GetMyParticipationsTest {

    @Test
    void 참여_목록을_200으로_반환한다() throws Exception {
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
          .perform(get("/v1/participations/me").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].participationId").value(500))
          .andExpect(jsonPath("$[0].buncheolId").value(10))
          .andExpect(jsonPath("$[0].buncheolTitle").value("뉴진스 1집 분철"))
          .andExpect(jsonPath("$[0].buncheolMemberCount").value(5))
          .andExpect(jsonPath("$[0].memberName").value("민지"))
          .andExpect(jsonPath("$[0].bidAmount").value(50_000))
          .andExpect(jsonPath("$[0].participationStatus").value("AWAITING_PAYMENT"))
          .andExpect(jsonPath("$[0].buncheolStatus").value("CLOSED"))
          .andExpect(jsonPath("$[0].closedRank").value(1));
    }

    @Test
    void 참여_내역이_없으면_빈_배열을_반환한다() throws Exception {
      given(myParticipationQueryService.getMyParticipations(PARTICIPANT_ID)).willReturn(List.of());

      mockMvc
          .perform(get("/v1/participations/me").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(content().string("[]"));
    }
  }

  @Nested
  @DisplayName("분철 참여 취소 API 테스트")
  class CancelParticipationTest {

    @Test
    void 참여_취소에_성공하면_204를_반환한다() throws Exception {
      mockMvc
          .perform(
              delete("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isNoContent());
    }

    @Test
    void 참여가_존재하지_않으면_404를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND))
          .given(buncheolCheckoutService)
          .cancelParticipation(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              delete("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PARTICIPATION_NOT_FOUND.getCode())));
    }

    @Test
    void 참여자가_아니면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION))
          .given(buncheolCheckoutService)
          .cancelParticipation(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              delete("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_NO_PERMISSION.getCode())));
    }

    @Test
    void ACTIVE_BID_상태가_아니면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID))
          .given(buncheolCheckoutService)
          .cancelParticipation(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              delete("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID.getCode())));
    }

    @Test
    void CAS_충돌_시_409와_취소_충돌_코드를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_CANCEL_CONFLICT))
          .given(buncheolCheckoutService)
          .cancelParticipation(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      mockMvc
          .perform(
              delete("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PARTICIPATION_CANCEL_CONFLICT.getCode())));
    }
  }

  private void setFieldValue(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
