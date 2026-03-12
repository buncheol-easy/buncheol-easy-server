package buncheoleasy.buncheol.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.ParticipationCheckoutInfo;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import java.lang.reflect.Field;
import java.util.Collections;
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

  @Nested
  @DisplayName("분철 참여 신청 및 결제 주문 생성 API 테스트")
  class StartCheckoutTest {

    @Test
    void 즉시_구매_참여_신청에_성공하면_201을_반환한다() throws Exception {
      // given
      Participation participation =
          Participation.createInstant(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 50_000L);
      setFieldValue(participation, "id", PARTICIPATION_ID);

      PaymentOrderInfo paymentOrderInfo =
          new PaymentOrderInfo(
              "clientKey", "order_123", "분철 즉시 구매 결제", 50_000L, "http://success", "http://fail");

      ParticipationCheckoutInfo checkoutInfo =
          new ParticipationCheckoutInfo(participation, paymentOrderInfo);

      given(
              buncheolCheckoutService.startCheckout(
                  eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class)))
          .willReturn(checkoutInfo);

      String requestBody =
          """
          {
            "buncheolMemberId": 10,
            "shippingAddressId": 200,
            "type": "INSTANT"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations/checkout", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .with(mockAuth()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.participationId").value(PARTICIPATION_ID))
          .andExpect(jsonPath("$.participationStatus").value("PAYMENT_PENDING"))
          .andExpect(jsonPath("$.clientKey").value("clientKey"))
          .andExpect(jsonPath("$.paymentOrderId").value("order_123"))
          .andExpect(jsonPath("$.amount").value(50_000));
    }

    @Test
    void 모집중이_아닌_분철이면_409를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheolCheckoutService)
          .startCheckout(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      String requestBody =
          """
          {
            "buncheolMemberId": 10,
            "shippingAddressId": 200,
            "type": "INSTANT"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations/checkout", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.BUNCHEOL_NOT_RECRUITING.getCode())));
    }

    @Test
    void 호스트가_참여하면_403을_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE))
          .given(buncheolCheckoutService)
          .startCheckout(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      String requestBody =
          """
          {
            "buncheolMemberId": 10,
            "shippingAddressId": 200,
            "type": "INSTANT"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations/checkout", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
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
      // given
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS))
          .given(buncheolCheckoutService)
          .startCheckout(eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class));

      String requestBody =
          """
          {
            "buncheolMemberId": 10,
            "shippingAddressId": 200,
            "type": "INSTANT"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations/checkout", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_ALREADY_EXISTS.getCode())));
    }

    @Test
    void 필수_필드가_누락되면_400을_반환한다() throws Exception {
      // given
      String requestBody =
          """
          {
            "buncheolMemberId": 10
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations/checkout", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .with(mockAuth()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("잔금 결제 주문 생성 API 테스트")
  class StartBalancePaymentCheckoutTest {

    @Test
    void 잔금_결제_주문_생성에_성공하면_201을_반환한다() throws Exception {
      // given
      PaymentOrderInfo paymentOrderInfo =
          new PaymentOrderInfo(
              "clientKey", "order_123", "분철 잔금 결제", 25_000L, "http://success", "http://fail");

      given(
              buncheolCheckoutService.startBalancePaymentCheckout(
                  eq(PARTICIPANT_ID), eq(PARTICIPATION_ID)))
          .willReturn(paymentOrderInfo);

      // when & then
      mockMvc
          .perform(
              post(
                      "/v1/participations/{participationId}/balance-payment/checkout",
                      PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.clientKey").value("clientKey"))
          .andExpect(jsonPath("$.paymentOrderId").value("order_123"))
          .andExpect(jsonPath("$.amount").value(25_000));
    }

    @Test
    void 참여가_존재하지_않으면_404를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND))
          .given(buncheolCheckoutService)
          .startBalancePaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      // when & then
      mockMvc
          .perform(
              post(
                      "/v1/participations/{participationId}/balance-payment/checkout",
                      PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PARTICIPATION_NOT_FOUND.getCode())));
    }

    @Test
    void 참여자가_아니면_403을_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION))
          .given(buncheolCheckoutService)
          .startBalancePaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      // when & then
      mockMvc
          .perform(
              post(
                      "/v1/participations/{participationId}/balance-payment/checkout",
                      PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_NO_PERMISSION.getCode())));
    }

    @Test
    void 잔금_결제가_허용되지_않는_상태이면_409를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED))
          .given(buncheolCheckoutService)
          .startBalancePaymentCheckout(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID));

      // when & then
      mockMvc
          .perform(
              post(
                      "/v1/participations/{participationId}/balance-payment/checkout",
                      PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED.getCode())));
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
