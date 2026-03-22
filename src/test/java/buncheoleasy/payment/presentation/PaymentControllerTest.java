package buncheoleasy.payment.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentService;
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
@DisplayName("PaymentController 테스트")
class PaymentControllerTest {

  private static final Long USER_ID = 100L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PaymentService paymentService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor mockAuth() {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Nested
  @DisplayName("결제 성공 콜백 테스트")
  class SuccessTest {

    @Test
    void 결제_승인에_성공하면_200을_반환한다() throws Exception {
      // given
      willDoNothing().given(paymentService).confirmPayment("toss_key", "order_123", 50_000L);

      // when & then
      mockMvc
          .perform(
              get("/v1/payments/success")
                  .param("paymentKey", "toss_key")
                  .param("orderId", "order_123")
                  .param("amount", "50000"))
          .andExpect(status().isOk());

      then(paymentService).should().confirmPayment("toss_key", "order_123", 50_000L);
    }

    @Test
    void 결제_승인_실패시_예외가_전파된다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_REJECTED))
          .given(paymentService)
          .confirmPayment("toss_key", "order_123", 50_000L);

      // when & then
      mockMvc
          .perform(
              get("/v1/payments/success")
                  .param("paymentKey", "toss_key")
                  .param("orderId", "order_123")
                  .param("amount", "50000"))
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PAYMENT_TOSS_CONFIRM_REJECTED.getCode())));
    }
  }

  @Nested
  @DisplayName("결제 실패 콜백 테스트")
  class FailTest {

    @Test
    void 결제_실패시_302_리다이렉트를_반환한다() throws Exception {
      // when & then
      mockMvc
          .perform(
              get("/v1/payments/fail")
                  .param("code", "PAY_CANCEL")
                  .param("message", "사용자 취소")
                  .param("orderId", "order_123"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", Matchers.containsString("/payment/fail.html")));
    }

    @Test
    void 파라미터가_없어도_리다이렉트에_성공한다() throws Exception {
      // when & then
      mockMvc
          .perform(get("/v1/payments/fail"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", Matchers.containsString("/payment/fail.html")));
    }
  }

  @Nested
  @DisplayName("결제 취소 테스트")
  class CancelPendingPaymentTest {

    @Test
    void 결제_취소에_성공하면_204를_반환한다() throws Exception {
      // given
      willDoNothing()
          .given(paymentService)
          .cancelPendingPayment(eq(USER_ID), eq("order_123"), any(), any());

      String requestBody =
          """
          {
            "code": "PAY_CANCEL",
            "message": "사용자 취소"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/v1/payments/{paymentOrderId}/cancel", "order_123")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .with(mockAuth()))
          .andExpect(status().isNoContent());

      then(paymentService)
          .should()
          .cancelPendingPayment(USER_ID, "order_123", "PAY_CANCEL", "사용자 취소");
    }

    @Test
    void 요청_본문_없이_취소에_성공한다() throws Exception {
      // given
      willDoNothing()
          .given(paymentService)
          .cancelPendingPayment(eq(USER_ID), eq("order_123"), any(), any());

      // when & then
      mockMvc
          .perform(post("/v1/payments/{paymentOrderId}/cancel", "order_123").with(mockAuth()))
          .andExpect(status().isNoContent());

      then(paymentService).should().cancelPendingPayment(USER_ID, "order_123", null, null);
    }

    @Test
    void 소유권_검증_실패시_403을_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PAYMENT_NO_PERMISSION))
          .given(paymentService)
          .cancelPendingPayment(eq(USER_ID), eq("order_123"), any(), any());

      // when & then
      mockMvc
          .perform(post("/v1/payments/{paymentOrderId}/cancel", "order_123").with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content().string(Matchers.containsString(ErrorCode.PAYMENT_NO_PERMISSION.getCode())));
    }

    @Test
    void 이미_승인된_결제_취소시_409를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_APPROVED))
          .given(paymentService)
          .cancelPendingPayment(eq(USER_ID), eq("order_123"), any(), any());

      // when & then
      mockMvc
          .perform(post("/v1/payments/{paymentOrderId}/cancel", "order_123").with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PAYMENT_ALREADY_APPROVED.getCode())));
    }

    @Test
    void 결제를_찾을_수_없으면_404를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
          .given(paymentService)
          .cancelPendingPayment(eq(USER_ID), eq("order_999"), any(), any());

      // when & then
      mockMvc
          .perform(post("/v1/payments/{paymentOrderId}/cancel", "order_999").with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(
              content().string(Matchers.containsString(ErrorCode.PAYMENT_NOT_FOUND.getCode())));
    }
  }
}
