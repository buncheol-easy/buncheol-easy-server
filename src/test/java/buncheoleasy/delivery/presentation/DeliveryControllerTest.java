package buncheoleasy.delivery.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.delivery.application.DeliveryService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Collections;
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
@DisplayName("DeliveryController 테스트")
class DeliveryControllerTest {

  private static final Long USER_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DeliveryService deliveryService;

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
  @DisplayName("운송장 등록 API 테스트")
  class RegisterTrackingTest {

    @Test
    void 정상_요청시_204를_반환한다() throws Exception {
      mockMvc
          .perform(
              patch("/v1/deliveries/{id}/tracking", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"trackingNumber\": \"TRACK123\"}"))
          .andExpect(status().isNoContent());

      then(deliveryService).should().registerTracking(USER_ID, 10L, "TRACK123");
    }

    @Test
    void trackingNumber가_비어있으면_400을_반환한다() throws Exception {
      mockMvc
          .perform(
              patch("/v1/deliveries/{id}/tracking", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"trackingNumber\": \"\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void 권한이_없으면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(deliveryService)
          .registerTracking(USER_ID, 10L, "TRACK123");

      mockMvc
          .perform(
              patch("/v1/deliveries/{id}/tracking", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"trackingNumber\": \"TRACK123\"}"))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      containsString(
                          ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }

    @Test
    void 배송_정보가_없으면_404를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.DELIVERY_NOT_FOUND))
          .given(deliveryService)
          .registerTracking(USER_ID, 999L, "TRACK123");

      mockMvc
          .perform(
              patch("/v1/deliveries/{id}/tracking", 999L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"trackingNumber\": \"TRACK123\"}"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("수령 확인 API 테스트")
  class ConfirmReceiptTest {

    @Test
    void 정상_요청시_204를_반환한다() throws Exception {
      mockMvc
          .perform(post("/v1/deliveries/{id}/receipt", 10L).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(deliveryService).should().confirmReceipt(USER_ID, 10L);
    }

    @Test
    void 권한이_없으면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.DELIVERY_NO_PERMISSION))
          .given(deliveryService)
          .confirmReceipt(USER_ID, 10L);

      mockMvc
          .perform(post("/v1/deliveries/{id}/receipt", 10L).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      containsString(
                          ErrorCode.DELIVERY_NO_PERMISSION.getCode())));
    }

    @Test
    void 상태_전이가_불가하면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID))
          .given(deliveryService)
          .confirmReceipt(USER_ID, 10L);

      mockMvc
          .perform(post("/v1/deliveries/{id}/receipt", 10L).with(mockAuth()))
          .andExpect(status().isConflict());
    }
  }
}
