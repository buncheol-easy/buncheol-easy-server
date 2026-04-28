package buncheoleasy.buncheol.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("BuncheolController 테스트")
class BuncheolControllerTest {

  private static final Long HOST_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor mockAuth() {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(HOST_ID, null, Collections.emptyList()));
      return request;
    };
  }

  private String validRequestJson() {
    LocalDateTime future = LocalDateTime.now().plusDays(7);
    return """
                {
                  "groupId": 100,
                  "title": "테스트 분철 제목",
                  "goodsName": "공식 앨범",
                  "storeName": "공식 스토어",
                  "originalPrice": 50000,
                  "deadline": "%s",
                  "shippingDeadlineDays": 7,
                  "gs25ShippingFee": 3000,
                  "settlementBank": "국민은행",
                  "settlementAccount": "123-456-789012",
                  "settlementHolder": "홍길동",
                  "buncheolMembers": [
                    {
                      "memberId": 200,
                      "instantPrice": 50000,
                      "bidAllowed": false
                    }
                  ]
                }
                """
        .formatted(future);
  }

  private String validModifyRequestJson() {
    LocalDateTime future = LocalDateTime.now().plusDays(10);
    return """
                {
                  "groupId": 100,
                  "title": "수정 분철 제목",
                  "goodsName": "수정 굿즈",
                  "storeName": "수정 스토어",
                  "originalPrice": 60000,
                  "deadline": "%s",
                  "shippingDeadlineDays": 5,
                  "gs25ShippingFee": 3500,
                  "settlementBank": "국민은행",
                  "settlementAccount": "333-222-111",
                  "settlementHolder": "홍길동",
                  "keepImageIds": [1, 2],
                  "buncheolMembers": [
                    {
                      "memberId": 200,
                      "instantPrice": 60000,
                      "bidAllowed": false
                    }
                  ]
                }
                """
        .formatted(future);
  }

  private MockMultipartHttpServletRequestBuilder modifyMultipartRequest(final Long buncheolId) {
    return multipart("/v1/buncheols/{id}", buncheolId)
        .with(
            request -> {
              request.setMethod("PUT");
              return request;
            });
  }

  @Nested
  @DisplayName("분철 개최 테스트")
  class HoldBuncheolTest {

    @Test
    void 분철_개최에_성공하면_201을_반환한다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isCreated());

      then(buncheolService).should().holdBuncheol(eq(HOST_ID), any(), any());
    }

    @Test
    void 이미지와_함께_분철_개최에_성공하면_201을_반환한다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());
      MockMultipartFile imagePart =
          new MockMultipartFile(
              "images", "album-cover.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(imagePart).with(mockAuth()))
          .andExpect(status().isCreated());
    }

    @Test
    void 제목이_없으면_400을_반환한다() throws Exception {
      // given
      LocalDateTime future = LocalDateTime.now().plusDays(7);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "goodsName": "공식 앨범",
                      "storeName": "공식 스토어",
                      "originalPrice": 50000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 7,
                      "gs25ShippingFee": 3000,
                      "settlementBank": "국민은행",
                      "settlementAccount": "123-456-789012",
                      "settlementHolder": "홍길동",
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 50000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(future);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void 배송비가_두_경우_모두_없으면_400을_반환한다() throws Exception {
      // given
      LocalDateTime future = LocalDateTime.now().plusDays(7);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "goodsName": "공식 앨범",
                      "storeName": "공식 스토어",
                      "originalPrice": 50000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 7,
                      "settlementBank": "국민은행",
                      "settlementAccount": "123-456-789012",
                      "settlementHolder": "홍길동",
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 50000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(future);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      willThrow(new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED))
          .given(buncheolService)
          .holdBuncheol(eq(HOST_ID), any(), any());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(
              content().string(containsString(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED.getCode())));
    }

    @Test
    void groupId가_없으면_400을_반환한다() throws Exception {
      // given
      LocalDateTime future = LocalDateTime.now().plusDays(7);
      String invalidJson =
          """
                    {
                      "title": "제목",
                      "goodsName": "공식 앨범",
                      "storeName": "공식 스토어",
                      "originalPrice": 50000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 7,
                      "gs25ShippingFee": 3000,
                      "settlementBank": "국민은행",
                      "settlementAccount": "123-456-789012",
                      "settlementHolder": "홍길동",
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 50000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(future);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void 마감일이_과거이면_400을_반환한다() throws Exception {
      // given
      LocalDateTime past = LocalDateTime.now().minusDays(1);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "goodsName": "공식 앨범",
                      "storeName": "공식 스토어",
                      "originalPrice": 50000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 7,
                      "gs25ShippingFee": 3000,
                      "settlementBank": "국민은행",
                      "settlementAccount": "123-456-789012",
                      "settlementHolder": "홍길동",
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 50000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(past);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());

      willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND))
          .given(buncheolService)
          .holdBuncheol(eq(HOST_ID), any(), any());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.GROUP_NOT_FOUND.getCode())));
    }
  }

  @Nested
  @DisplayName("분철 수정 테스트")
  class ModifyBuncheolTest {

    @Test
    void 분철_수정에_성공하면_204를_반환한다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService).should().modifyBuncheol(eq(HOST_ID), eq(10L), any(), any());
    }

    @Test
    void 이미지와_함께_분철_수정에_성공하면_204를_반환한다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());
      MockMultipartFile imagePart =
          new MockMultipartFile(
              "images", "new-image.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).file(imagePart).with(mockAuth()))
          .andExpect(status().isNoContent());
    }

    @Test
    void keepImageIds가_없으면_400을_반환한다() throws Exception {
      // given
      LocalDateTime future = LocalDateTime.now().plusDays(10);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "수정 분철 제목",
                      "goodsName": "수정 굿즈",
                      "storeName": "수정 스토어",
                      "originalPrice": 60000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 5,
                      "gs25ShippingFee": 3500,
                      "settlementBank": "국민은행",
                      "settlementAccount": "333-222-111",
                      "settlementHolder": "홍길동",
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 60000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(future);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void groupId가_없으면_400을_반환한다() throws Exception {
      // given
      LocalDateTime future = LocalDateTime.now().plusDays(10);
      String invalidJson =
          """
                    {
                      "title": "수정 분철 제목",
                      "goodsName": "수정 굿즈",
                      "storeName": "수정 스토어",
                      "originalPrice": 60000,
                      "deadline": "%s",
                      "shippingDeadlineDays": 5,
                      "gs25ShippingFee": 3500,
                      "settlementBank": "국민은행",
                      "settlementAccount": "333-222-111",
                      "settlementHolder": "홍길동",
                      "keepImageIds": [],
                      "buncheolMembers": [
                        {"memberId": 200, "instantPrice": 60000, "bidAllowed": false}
                      ]
                    }
                    """
              .formatted(future);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
      // given
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND))
          .given(buncheolService)
          .modifyBuncheol(eq(HOST_ID), eq(10L), any(), any());

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_FOUND.getCode())));
    }
  }

  @Nested
  @DisplayName("분철 취소 테스트")
  class CancelBuncheolTest {

    @Test
    void 분철_취소에_성공하면_204를_반환한다() throws Exception {
      // when & then
      mockMvc
          .perform(delete("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService).should().cancelBuncheol(HOST_ID, 10L);
    }

    @Test
    void 취소불가_상태면_409를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED))
          .given(buncheolService)
          .cancelBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(delete("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content().string(containsString(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED.getCode())));
    }

    @Test
    void 분철이_없으면_404를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND))
          .given(buncheolService)
          .cancelBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(delete("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_FOUND.getCode())));
    }

    @Test
    void 소유자가_아니면_403을_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheolService)
          .cancelBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(delete("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }
  }

  @Nested
  @DisplayName("분철 상태 진행 API 테스트")
  class AdvanceBuncheolStatusTest {

    @Test
    void 정상_요청시_204를_반환한다() throws Exception {
      // when & then
      mockMvc
          .perform(
              patch("/v1/buncheols/{id}/status", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\": \"GOODS_ORDERED\"}"))
          .andExpect(status().isNoContent());

      then(buncheolService)
          .should()
          .advanceBuncheolStatus(HOST_ID, 10L, BuncheolStatus.GOODS_ORDERED);
    }

    @Test
    void status가_없으면_400을_반환한다() throws Exception {
      mockMvc
          .perform(
              patch("/v1/buncheols/{id}/status", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void 전이_불가한_상태면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED))
          .given(buncheolService)
          .advanceBuncheolStatus(HOST_ID, 10L, BuncheolStatus.GOODS_ORDERED);

      mockMvc
          .perform(
              patch("/v1/buncheols/{id}/status", 10L)
                  .with(mockAuth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\": \"GOODS_ORDERED\"}"))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(containsString(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED.getCode())));
    }
  }
}
