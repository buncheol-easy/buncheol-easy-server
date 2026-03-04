package buncheoleasy.user.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.application.ShippingAddressService;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("ShippingAddressController 테스트")
class ShippingAddressControllerTest {

  private static final Long USER_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ShippingAddressService shippingAddressService;

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

  @Test
  void 배송지_등록이_성공하면_201을_반환한다() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/me/shipping-addresses")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"GS25 강남역점\"}"))
        .andExpect(status().isCreated());

    then(shippingAddressService)
        .should()
        .registerShippingAddress(
            USER_ID,
            new buncheoleasy.user.dto.request.ShippingAddressRequest("GS25_HALF", "GS25 강남역점"));
  }

  @Test
  void 배송지_등록_요청_검증에_실패하면_400과_표준_에러코드를_반환한다() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/me/shipping-addresses")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
  }

  @Test
  void 배송지_수정_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
    willThrow(new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN))
        .given(shippingAddressService)
        .modifyShippingAddress(
            USER_ID,
            10L,
            new buncheoleasy.user.dto.request.ShippingAddressRequest("CU_HALF", "CU 홍대입구점"));

    mockMvc
        .perform(
            put("/v1/users/me/shipping-addresses/10")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingMethod\":\"CU_HALF\",\"storeName\":\"CU 홍대입구점\"}"))
        .andExpect(status().isForbidden())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        ErrorCode.SHIPPING_ADDRESS_FORBIDDEN.getCode())));
  }

  @Test
  void 배송지_삭제_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
    willThrow(new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND))
        .given(shippingAddressService)
        .removeShippingAddress(USER_ID, 10L);

    mockMvc
        .perform(delete("/v1/users/me/shipping-addresses/10").with(mockAuth()))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        ErrorCode.SHIPPING_ADDRESS_NOT_FOUND.getCode())));
  }

  @Test
  void 내_배송지_목록_조회가_성공하면_200과_응답목록을_반환한다() throws Exception {
    given(shippingAddressService.getUserShippingAddresses(USER_ID))
        .willReturn(
            List.of(
                ShippingAddressResponse.of(1L, "GS25_HALF", "GS25 강남역점"),
                ShippingAddressResponse.of(2L, "CU_HALF", "CU 홍대입구점")));

    mockMvc
        .perform(get("/v1/users/me/shipping-addresses").with(mockAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1L))
        .andExpect(jsonPath("$[0].shippingMethod").value("GS25_HALF"))
        .andExpect(jsonPath("$[1].id").value(2L))
        .andExpect(jsonPath("$[1].shippingMethod").value("CU_HALF"));
  }
}
