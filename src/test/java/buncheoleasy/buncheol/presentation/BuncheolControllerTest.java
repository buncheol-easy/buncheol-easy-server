package buncheoleasy.buncheol.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.BuncheolTermsChange;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolImageResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementParticipantResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberSaleStatus;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationItemResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.RequestedShippingAddressResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.ResultActions;
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

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;

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
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    return """
                {
                  "groupId": 100,
                  "title": "테스트 분철 제목",
                  "purchaseSite": "공식 스토어",
                  "deadline": "%s",
                  "minHeadcount": 3,
                  "gs25ShippingFee": 3000,
                  "thumbnailIndex": 0,
                  "buncheolMembers": [
                    {
                      "memberId": 200,
                      "price": 50000
                    }
                  ]
                }
                """
        .formatted(deadline);
  }

  private String validModifyRequestJson() {
    return """
                {
                  "title": "수정 분철 제목",
                  "description": "수정 설명",
                  "keepImageIds": [1, 2],
                  "thumbnailImageId": 1
                }
                """;
  }

  private MockMultipartHttpServletRequestBuilder modifyMultipartRequest(final Long buncheolId) {
    return multipart("/v1/buncheols/{id}", buncheolId)
        .with(
            request -> {
              request.setMethod("PUT");
              return request;
            });
  }

  // 분철 개최는 이미지가 필수(@RequestPart required=true)라, request 검증/서비스 동작을 보려면 유효한 images 파트가 함께 와야 한다.
  private MockMultipartFile validImagePart() {
    return new MockMultipartFile(
        "images", "album-cover.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  @Nested
  @DisplayName("분철 개최 테스트")
  class HoldBuncheolTest {

    @Test
    void 이미지_파트가_없으면_400을_반환한다() throws Exception {
      // given — 이미지는 필수(@RequestPart required=true). images 파트가 아예 없으면 400.
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());

      // when & then — images 파트를 일부러 빼고 호출한다.
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

      then(buncheolService).should(never()).holdBuncheol(any(), any(), any());
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
    void 배송비가_0원이어도_분철_개최에_성공하면_201을_반환한다() throws Exception {
      // given — 0원은 개최자가 배송비를 받지 않는 무료 배송으로 허용한다(@PositiveOrZero).
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String zeroShippingFeeJson =
          """
                    {
                      "groupId": 100,
                      "title": "테스트 분철 제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": 0,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, zeroShippingFeeJson.getBytes());

      // when & then — 201 은 DTO 검증 통과까지만 보증하므로, 0원이 매핑 중 탈락하지 않고 서비스까지
      // 전달되는지도 함께 검증한다.
      mockMvc
          .perform(
              multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isCreated());

      then(buncheolService)
          .should()
          .holdBuncheol(
              eq(HOST_ID),
              argThat(request -> request.gs25ShippingFee() == 0 && request.cuShippingFee() == null),
              any());
    }

    @Test
    void 배송비가_음수면_400을_반환한다() throws Exception {
      // given — 음수는 DTO 검증(@PositiveOrZero)에서 걸러진다.
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String negativeShippingFeeJson =
          """
                    {
                      "groupId": 100,
                      "title": "테스트 분철 제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": -100,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, negativeShippingFeeJson.getBytes());

      // when & then
      mockMvc
          .perform(
              multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).holdBuncheol(any(), any(), any());
    }

    @Test
    void 제목이_없으면_400을_반환한다() throws Exception {
      // given
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": 3000,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void thumbnailIndex가_없으면_400을_반환한다() throws Exception {
      // given — 대표사진 인덱스는 필수(@NotNull). 누락 시 DTO 검증에서 걸러진다.
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": 3000,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).holdBuncheol(any(), any(), any());
    }

    @Test
    void minHeadcount가_없으면_400을_반환한다() throws Exception {
      // given
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "gs25ShippingFee": 3000,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void 배송비가_두_경우_모두_없으면_400을_반환한다() throws Exception {
      // given
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      willThrow(new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED))
          .given(buncheolService)
          .holdBuncheol(eq(HOST_ID), any(), any());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(
              content().string(containsString(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED.getCode())));
    }

    @Test
    void groupId가_없으면_400을_반환한다() throws Exception {
      // given
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": 3000,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    @Test
    void 마감일이_과거이면_400을_반환한다() throws Exception {
      // given
      Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "minHeadcount": 3,
                      "gs25ShippingFee": 3000,
                      "thumbnailIndex": 0,
                      "buncheolMembers": [
                        {"memberId": 200, "price": 50000}
                      ]
                    }
                    """
              .formatted(past);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
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
          .perform(multipart("/v1/buncheols").file(requestPart).file(validImagePart()).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.GROUP_NOT_FOUND.getCode())));
    }

    // 핵심 회귀 지점은 레코드가 아니라 컨트롤러 — toImageFile 의 catch 가 IOException 을 넘어
    // BusinessException 까지 삼키도록 넓어지면 ImageFileTest 는 초록인 채 조용히 회귀한다.
    @Test
    void 허용되지_않은_확장자가_섞이면_201_전에_400_FILE_002_로_거부하고_서비스를_호출하지_않는다()
        throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());
      MockMultipartFile gif =
          new MockMultipartFile("images", "s.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1});

      mockMvc
          .perform(
              multipart("/v1/buncheols")
                  .file(requestPart)
                  .file(validImagePart())
                  .file(gif)
                  .with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(ErrorCode.FILE_EXTENSION_INVALID.getCode()));

      then(buncheolService).should(never()).holdBuncheol(any(), any(), any());
    }

    // #224 — 보낸 Content-Type 으로 거절하지 않고 덮어쓴다. 지금 201 이던 요청은 계속 201 이고, 서비스에는 확장자로 정한 형식이 간다.
    @Test
    void 이미지_파트의_Content_Type_이_HTML_이어도_201이고_저장_형식은_확장자로_정해진다() throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());
      MockMultipartFile html =
          new MockMultipartFile("images", "evil.png", MediaType.TEXT_HTML_VALUE, new byte[] {1});

      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(html).with(mockAuth()))
          .andExpect(status().isCreated());

      assertThat(capturedHoldImages())
          .extracting(ImageFile::contentType)
          .containsExactly("image/png");
    }

    @Test
    void 이미지_파트에_Content_Type_이_없어도_201이고_저장_형식은_확장자로_정해진다() throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validRequestJson().getBytes());
      MockMultipartFile noType =
          new MockMultipartFile("images", "buncheol-1.jpg", null, new byte[] {1});

      mockMvc
          .perform(multipart("/v1/buncheols").file(requestPart).file(noType).with(mockAuth()))
          .andExpect(status().isCreated());

      assertThat(capturedHoldImages())
          .extracting(ImageFile::contentType)
          .containsExactly("image/jpeg");
    }

    @SuppressWarnings("unchecked")
    private List<ImageFile> capturedHoldImages() {
      ArgumentCaptor<List<ImageFile>> captor = ArgumentCaptor.forClass((Class) List.class);
      then(buncheolService).should().holdBuncheol(eq(HOST_ID), any(), captor.capture());
      return captor.getValue();
    }
  }

  @Nested
  @DisplayName("오픈채팅 링크 수정 테스트")
  class UpdateOpenChatUrlTest {

    private ResultActions perform(final String body) throws Exception {
      return mockMvc.perform(
          patch("/v1/buncheols/{id}/open-chat-url", 10L)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body)
              .with(mockAuth()));
    }

    @Test
    void 링크_수정에_성공하면_204를_반환한다() throws Exception {
      perform("{\"openChatUrl\": \"https://open.kakao.com/o/gAbCdEf\"}")
          .andExpect(status().isNoContent());

      then(buncheolService)
          .should()
          .updateOpenChatUrl(HOST_ID, 10L, "https://open.kakao.com/o/gAbCdEf");
    }

    @Test
    void 빈_문자열은_링크_제거로_그대로_전달된다() throws Exception {
      perform("{\"openChatUrl\": \"\"}").andExpect(status().isNoContent());

      then(buncheolService).should().updateOpenChatUrl(HOST_ID, 10L, "");
    }

    // 누락을 제거로 받으면 폼 조립 실수가 링크를 조용히 지운다 — DTO @NotNull 이 웹 레이어에서 막는지 고정한다.
    @Test
    void 필드를_빠뜨리면_400이고_서비스를_호출하지_않는다() throws Exception {
      perform("{}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).updateOpenChatUrl(any(), any(), any());
    }

    // 길이 상한은 DTO(@Size → C-001)와 도메인(BCH-088)이 함께 든다 — 웹 레이어가 먼저 걸러 서비스에 닿지 않는다.
    @Test
    void 상한을_넘기면_400이고_서비스를_호출하지_않는다() throws Exception {
      String tooLong = "https://open.kakao.com/o/" + "a".repeat(200);

      perform("{\"openChatUrl\": \"" + tooLong + "\"}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).updateOpenChatUrl(any(), any(), any());
    }

    @Test
    void 개최자가_아니면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheolService)
          .updateOpenChatUrl(eq(HOST_ID), eq(10L), any());

      perform("{\"openChatUrl\": \"https://open.kakao.com/o/gAbCdEf\"}")
          .andExpect(status().isForbidden())
          .andExpect(
              content().string(containsString(ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }

    @Test
    void 취소된_분철이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE))
          .given(buncheolService)
          .updateOpenChatUrl(eq(HOST_ID), eq(10L), any());

      perform("{\"openChatUrl\": \"https://open.kakao.com/o/gAbCdEf\"}")
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      containsString(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_NOT_EDITABLE.getCode())));
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
      String invalidJson =
          """
                    {
                      "title": "수정 분철 제목",
                      "description": "수정 설명"
                    }
                    """;

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
    void title이_없으면_400을_반환한다() throws Exception {
      // given
      String invalidJson =
          """
                    {
                      "description": "수정 설명",
                      "keepImageIds": []
                    }
                    """;

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

      // when & then
      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
    }

    // 옛 클라이언트는 조건 칸을 모른다 — 전부 null(유지)로 서비스에 닿아야 배포 전과 결과가 같다.
    @Test
    void 조건_칸이_없는_요청은_조건을_전부_null_로_넘긴다() throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());

      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService)
          .should()
          .modifyBuncheol(
              eq(HOST_ID),
              eq(10L),
              argThat(
                  request ->
                      request.title().equals("수정 분철 제목")
                          && request.toTermsChange().equals(BuncheolTermsChange.none())),
              any());
    }

    @Test
    void 조건_칸을_실으면_그대로_서비스에_넘긴다() throws Exception {
      String json =
          """
          {
            "title": "수정 분철 제목",
            "keepImageIds": [1],
            "thumbnailImageId": 1,
            "deadline": "2026-10-20T03:00:00Z",
            "minHeadcount": 2,
            "gs25ShippingFee": null,
            "cuShippingFee": 2500,
            "purchaseSite": "새 구매처"
          }
          """;
      MockMultipartFile requestPart =
          new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService)
          .should()
          .modifyBuncheol(
              eq(HOST_ID),
              eq(10L),
              argThat(
                  request ->
                      request
                          .toTermsChange()
                          .equals(
                              new BuncheolTermsChange(
                                  Instant.parse("2026-10-20T03:00:00Z"),
                                  2,
                                  null,
                                  2500,
                                  "새 구매처"))),
              any());
    }

    @Test
    void 신청자가_있어_조건을_못_바꾸면_409와_전용_코드를_반환한다() throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT))
          .given(buncheolService)
          .modifyBuncheol(eq(HOST_ID), eq(10L), any(), any());

      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(containsString(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT.getCode())));
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

    // 수정은 기존 사진을 트랜잭션 안에서 먼저 지우므로, 서비스까지 가면 사진이 0장이 된다 — 서비스 전에 막는지 고정한다.
    @Test
    void 허용되지_않은_확장자면_204_전에_400_FILE_002_로_거부하고_서비스를_호출하지_않는다()
        throws Exception {
      String keepNothingJson =
          """
                    {
                      "title": "수정 분철 제목",
                      "keepImageIds": [],
                      "thumbnailIndex": 0
                    }
                    """;
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, keepNothingJson.getBytes());
      MockMultipartFile gif =
          new MockMultipartFile("images", "s.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1});

      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).file(gif).with(mockAuth()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(ErrorCode.FILE_EXTENSION_INVALID.getCode()));

      then(buncheolService).should(never()).modifyBuncheol(any(), any(), any(), any());
    }

    // #224 — 수정도 개최와 같은 레코드를 거친다. 보낸 Content-Type 으로 거절하지 않고(204 그대로) 확장자로 정한 형식이 간다.
    @Test
    @SuppressWarnings("unchecked")
    void 이미지_파트의_Content_Type_이_HTML_이어도_204이고_저장_형식은_확장자로_정해진다() throws Exception {
      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request", "", MediaType.APPLICATION_JSON_VALUE, validModifyRequestJson().getBytes());
      MockMultipartFile html =
          new MockMultipartFile("images", "evil.png", MediaType.TEXT_HTML_VALUE, new byte[] {1});

      mockMvc
          .perform(modifyMultipartRequest(10L).file(requestPart).file(html).with(mockAuth()))
          .andExpect(status().isNoContent());

      ArgumentCaptor<List<ImageFile>> captor = ArgumentCaptor.forClass((Class) List.class);
      then(buncheolService).should().modifyBuncheol(eq(HOST_ID), eq(10L), any(), captor.capture());
      assertThat(captor.getValue()).extracting(ImageFile::contentType).containsExactly("image/png");
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
  @DisplayName("C2C 자리 빼기 API 테스트")
  class RemoveMemberTest {

    @Test
    void 자리를_빼면_204를_반환한다() throws Exception {
      mockMvc
          .perform(
              delete("/v1/buncheols/{id}/members/{buncheolMemberId}", 10L, 7L)
                  .with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService).should().removeMember(HOST_ID, 10L, 7L);
    }

    @Test
    void 신청자가_있는_자리면_409와_전용_코드를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT))
          .given(buncheolService)
          .removeMember(HOST_ID, 10L, 7L);

      mockMvc
          .perform(
              delete("/v1/buncheols/{id}/members/{buncheolMemberId}", 10L, 7L)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      containsString(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_APPLICANT.getCode())));
    }
  }

  @Nested
  @DisplayName("C2C 빈 자리 가격 바꾸기 API 테스트")
  class ChangeMemberPriceTest {

    private ResultActions perform(final String body) throws Exception {
      return mockMvc.perform(
          patch("/v1/buncheols/{id}/members/{buncheolMemberId}", 10L, 7L)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body)
              .with(mockAuth()));
    }

    @Test
    void 가격을_바꾸면_204를_반환한다() throws Exception {
      perform("{\"price\": 15000}").andExpect(status().isNoContent());

      then(buncheolService).should().changeMemberPrice(HOST_ID, 10L, 7L, 15_000L);
    }

    @Test
    void 가격이_없으면_400이고_서비스를_호출하지_않는다() throws Exception {
      perform("{}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).changeMemberPrice(any(), any(), any(), anyLong());
    }

    @Test
    void 가격이_음수면_400이고_서비스를_호출하지_않는다() throws Exception {
      perform("{\"price\": -100}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).changeMemberPrice(any(), any(), any(), anyLong());
    }

    @Test
    void 신청자가_있는_자리면_409와_전용_코드를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_MEMBER_PRICE_CHANGE_NOT_ALLOWED))
          .given(buncheolService)
          .changeMemberPrice(HOST_ID, 10L, 7L, 15_000L);

      perform("{\"price\": 15000}")
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      containsString(ErrorCode.BUNCHEOL_MEMBER_PRICE_CHANGE_NOT_ALLOWED.getCode())));
    }
  }

  @Nested
  @DisplayName("C2C 자리 다시 넣기 API 테스트")
  class AddMemberTest {

    private ResultActions perform(final String body) throws Exception {
      return mockMvc.perform(
          post("/v1/buncheols/{id}/members", 10L)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body)
              .with(mockAuth()));
    }

    @Test
    void 자리를_넣으면_201과_새_자리_id_를_반환한다() throws Exception {
      given(
              buncheolService.addMember(
                  eq(HOST_ID),
                  eq(10L),
                  argThat(r -> r.memberId().equals(200L) && r.price().equals(15_000L))))
          .willReturn(30L);

      perform("{\"memberId\": 200, \"price\": 15000}")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.buncheolMemberId").value(30));
    }

    @Test
    void memberId_가_없으면_400이고_서비스를_호출하지_않는다() throws Exception {
      perform("{\"price\": 15000}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).addMember(any(), any(), any());
    }

    @Test
    void 가격이_음수면_400이고_서비스를_호출하지_않는다() throws Exception {
      perform("{\"memberId\": 200, \"price\": -100}")
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));

      then(buncheolService).should(never()).addMember(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("내 개최 분철 목록 조회 API 테스트")
  class GetMyHostedBuncheolsTest {

    @Test
    void 개최한_분철_목록을_200으로_반환한다() throws Exception {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
      MyHostedBuncheolResponse response =
          new MyHostedBuncheolResponse(
              10L,
              "뉴진스 1집 분철",
              "뉴진스",
              BuncheolStatus.RECRUITING,
              deadline,
              5,
              7L,
              createdAt,
              "https://cdn.example.com/buncheol-10-thumb.jpg",
              FlowType.LEGACY,
              BuncheolHostCancellability.CANCELLABLE,
              false);
      given(myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID))
          .willReturn(List.of(response));

      mockMvc
          .perform(get("/v1/buncheols/me").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].buncheolId").value(10))
          .andExpect(jsonPath("$[0].title").value("뉴진스 1집 분철"))
          .andExpect(jsonPath("$[0].groupName").value("뉴진스"))
          .andExpect(jsonPath("$[0].status").value("RECRUITING"))
          .andExpect(jsonPath("$[0].memberSlotCount").value(5))
          .andExpect(jsonPath("$[0].activeParticipationCount").value(7))
          .andExpect(
              jsonPath("$[0].thumbnailUrl")
                  .value("https://cdn.example.com/buncheol-10-thumb.jpg"));
    }

    @Test
    void 개최한_분철이_없으면_빈_배열을_반환한다() throws Exception {
      given(myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID)).willReturn(List.of());

      mockMvc
          .perform(get("/v1/buncheols/me").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(content().string("[]"));
    }
  }

  @Nested
  @DisplayName("개최 자격 사전 조회 API 테스트 (docs/53 Q-07)")
  class GetHostingEligibilityTest {

    @Test
    void 적격이면_eligible_true_와_null_사유를_반환한다() throws Exception {
      given(buncheolService.getHostingEligibility(HOST_ID))
          .willReturn(HostingEligibilityResponse.allowed());

      mockMvc
          .perform(get("/v1/buncheols/hosting-eligibility").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.eligible").value(true))
          .andExpect(jsonPath("$.reason").value(nullValue()));
    }

    @Test
    void 부적격이면_사유를_함께_반환한다() throws Exception {
      given(buncheolService.getHostingEligibility(HOST_ID))
          .willReturn(
              HostingEligibilityResponse.blocked(HostingEligibilityResponse.Reason.NOT_ADULT));

      mockMvc
          .perform(get("/v1/buncheols/hosting-eligibility").with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.eligible").value(false))
          .andExpect(jsonPath("$.reason").value("NOT_ADULT"));
    }
  }

  @Nested
  @DisplayName("분철 목록 조회 API 테스트")
  class SearchBuncheolsTest {

    @Test
    void keyword_가_100자_초과면_400과_INVALID_INPUT_VALUE를_반환한다() throws Exception {
      // given
      String overLimit = "a".repeat(101);

      // when & then
      mockMvc
          .perform(get("/v1/buncheols").queryParam("keyword", overLimit))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

      then(buncheolListQueryService).shouldHaveNoInteractions();
    }

    @Test
    void 비로그인_호출은_userId_null_로_전달되고_bookmarked_가_false_로_내려간다() throws Exception {
      // given
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      BuncheolSummaryResponse item =
          new BuncheolSummaryResponse(
              10L,
              "뉴진스 1집 분철",
              BuncheolStatus.RECRUITING,
              FlowType.LEGACY,
              deadline,
              3,
              false,
              "뉴진스",
              null,
              List.of("민지"),
              List.of("민지"),
              false,
              false,
              Instant.parse("2026-05-15T08:00:00Z"),
              null);
      CursorResponse<BuncheolSummaryResponse> response =
          new CursorResponse<>(List.of(item), null, false);

      given(
              buncheolListQueryService.search(
                  null,
                  new BuncheolSearchCondition(null, null, null),
                  BuncheolListCursor.firstPage(),
                  20))
          .willReturn(response);

      // when & then
      mockMvc
          .perform(get("/v1/buncheols"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].bookmarked").value(false))
          // 클라가 Date.parse 로 '신규' 배지를 판정하므로 숫자 타임스탬프가 아닌 ISO-8601 문자열이어야 한다.
          .andExpect(jsonPath("$.items[0].createdAt").value("2026-05-15T08:00:00Z"))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }
  }

  @Nested
  @DisplayName("분철 단건 상세 조회 API 테스트")
  class GetBuncheolDetailTest {

    @Test
    void 로그인_유저는_myParticipation_을_포함해_200으로_반환한다() throws Exception {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      BuncheolDetailResponse response =
          new BuncheolDetailResponse(
              10L,
              "뉴진스 1집 분철",
              "뉴진스",
              "공식 스토어",
              deadline,
              "분철 설명",
              BuncheolStatus.RECRUITING,
              3,
              1,
              List.of(new BuncheolImageResponse(11L, "https://cdn/img1.jpg", true)),
              List.of(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000)),
              List.of(
                  new BuncheolMemberDetailResponse(
                      101L, 1001L, "민지", "minji.png", 50_000L,
                      BuncheolMemberSaleStatus.AWAITING_PAYMENT,
                      Instant.parse("2026-05-20T10:30:00Z"),
                      true)),
              true,
              new MyParticipationSummaryResponse(
                  1,
                  List.of(
                      new MyParticipationItemResponse(
                          601L, 101L, ParticipationStatus.AWAITING_PAYMENT)),
                  true,
                  new RequestedShippingAddressResponse("GS25_HALF", "GS25 강남역점")),
              FlowType.LEGACY,
              null,
              null,
              false,
              null,
              Instant.parse("2026-05-14T12:00:00Z"));
      given(buncheolDetailQueryService.getDetail(10L, HOST_ID)).willReturn(response);

      mockMvc
          .perform(get("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.groupName").value("뉴진스"))
          .andExpect(jsonPath("$.status").value("RECRUITING"))
          .andExpect(jsonPath("$.minHeadcount").value(3))
          .andExpect(jsonPath("$.confirmedCount").value(1))
          .andExpect(jsonPath("$.hostedByMe").value(true))
          .andExpect(jsonPath("$.images[0].id").value(11))
          .andExpect(jsonPath("$.images[0].url").value("https://cdn/img1.jpg"))
          .andExpect(jsonPath("$.images[0].thumbnail").value(true))
          .andExpect(jsonPath("$.shippingOptions[0].method").value("GS25_HALF"))
          .andExpect(jsonPath("$.shippingOptions[0].fee").value(3000))
          .andExpect(jsonPath("$.members[0].buncheolMemberId").value(101))
          .andExpect(jsonPath("$.members[0].price").value(50000))
          .andExpect(jsonPath("$.members[0].saleStatus").value("AWAITING_PAYMENT"))
          .andExpect(jsonPath("$.members[0].paymentDueAt").value("2026-05-20T10:30:00Z"))
          .andExpect(jsonPath("$.members[0].participatedByMe").value(true))
          .andExpect(jsonPath("$.myParticipation.participatedMemberCount").value(1))
          .andExpect(jsonPath("$.myParticipation.participations[0].participationId").value(601))
          .andExpect(jsonPath("$.myParticipation.participations[0].buncheolMemberId").value(101))
          .andExpect(jsonPath("$.myParticipation.participations[0].status").value("AWAITING_PAYMENT"))
          .andExpect(jsonPath("$.termsEditable").value(false));
    }

    @Test
    void 비로그인_호출은_userId_null_로_전달되고_myParticipation_은_null_로_내려간다() throws Exception {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      BuncheolDetailResponse response =
          new BuncheolDetailResponse(
              10L,
              "뉴진스 1집 분철",
              "뉴진스",
              "공식 스토어",
              deadline,
              null,
              BuncheolStatus.RECRUITING,
              3,
              0,
              List.of(),
              List.of(new ShippingOptionResponse(ShippingMethod.CU_HALF, 4000)),
              List.of(),
              false,
              null, FlowType.LEGACY, null, null, false, null, Instant.now());
      given(buncheolDetailQueryService.getDetail(10L, null)).willReturn(response);

      mockMvc
          .perform(get("/v1/buncheols/{id}", 10L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hostedByMe").value(false))
          .andExpect(jsonPath("$.myParticipation").doesNotExist());

      then(buncheolDetailQueryService).should().getDetail(eq(10L), isNull());
    }

    @Test
    void CANCELLED_분철도_status_CANCELLED_로_200을_반환한다() throws Exception {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      BuncheolDetailResponse response =
          new BuncheolDetailResponse(
              10L,
              "뉴진스 1집 분철",
              "뉴진스",
              "공식 스토어",
              deadline,
              null,
              BuncheolStatus.CANCELLED,
              3,
              0,
              List.of(),
              List.of(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000)),
              List.of(),
              false,
              null, FlowType.LEGACY, null, null, false, null, Instant.now());
      given(buncheolDetailQueryService.getDetail(10L, null)).willReturn(response);

      mockMvc
          .perform(get("/v1/buncheols/{id}", 10L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void 존재하지_않는_분철이면_404를_반환한다() throws Exception {
      given(buncheolDetailQueryService.getDetail(10L, HOST_ID))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      mockMvc
          .perform(get("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_FOUND.getCode())));
    }
  }

  @Nested
  @DisplayName("개최자 분철 관리 화면 조회 API 테스트")
  class GetBuncheolManagementTest {

    @Test
    void 호스트_본인이_조회하면_참여자_입금자명_배송_정보까지_200으로_반환한다() throws Exception {
      Instant deadline = Instant.parse("2026-05-27T00:00:00Z");
      BuncheolManagementParticipantResponse confirmed =
          new BuncheolManagementParticipantResponse(
              601L,
              9001L,
              9L,
              "유진팬",
              101L,
              "안유진",
              "유진팬",
              93_000L,
              3_000L,
              ParticipationStatus.CONFIRMED,
              Instant.parse("2026-05-28T00:00:00Z"),
              Instant.parse("2026-05-27T10:00:00Z"),
              null,
              new ManagementDeliveryResponse(
                  5001L,
                  ShippingMethod.GS25_HALF,
                  "GS25 강남역점",
                  "유진팬",
                  "010-1234-5678",
                  "1234567890",
                  DeliveryStatus.SHIPPING), null,
              null,
              true);
      BuncheolManagementParticipantResponse awaiting =
          new BuncheolManagementParticipantResponse(
              602L,
              9001L,
              9L,
              "레이팬",
              102L,
              "레이",
              "레이팬",
              53_000L,
              3_000L,
              ParticipationStatus.AWAITING_PAYMENT,
              Instant.parse("2026-05-26T00:30:00Z"),
              null,
              null,
              null, null,
              null,
              true);
      BuncheolManagementResponse response =
          new BuncheolManagementResponse(
              10L,
              "호두 자랑",
              "IVE",
              "호두네",
              BuncheolStatus.CONFIRMED,
              deadline,
              4,
              4,
              1,
              List.of(confirmed, awaiting), List.of(), FlowType.LEGACY, null, null, false);
      given(buncheolManagementQueryService.getManagement(10L, HOST_ID)).willReturn(response);

      mockMvc
          .perform(get("/v1/buncheols/{id}/management", 10L).with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.title").value("호두 자랑"))
          .andExpect(jsonPath("$.groupName").value("IVE"))
          .andExpect(jsonPath("$.purchaseSite").value("호두네"))
          .andExpect(jsonPath("$.status").value("CONFIRMED"))
          .andExpect(jsonPath("$.minHeadcount").value(4))
          .andExpect(jsonPath("$.memberCount").value(4))
          .andExpect(jsonPath("$.confirmedCount").value(1))
          .andExpect(jsonPath("$.participants[0].participationId").value(601))
          .andExpect(jsonPath("$.participants[0].participantNickname").value("유진팬"))
          .andExpect(jsonPath("$.participants[0].memberName").value("안유진"))
          .andExpect(jsonPath("$.participants[0].amount").value(93000))
          .andExpect(jsonPath("$.participants[0].status").value("CONFIRMED"))
          .andExpect(jsonPath("$.participants[0].depositorName").value("유진팬"))
          .andExpect(jsonPath("$.participants[0].refundAccount").doesNotExist())
          .andExpect(jsonPath("$.participants[0].delivery.deliveryId").value(5001))
          .andExpect(jsonPath("$.participants[0].delivery.shippingMethod").value("GS25_HALF"))
          .andExpect(jsonPath("$.participants[0].delivery.trackingNumber").value("1234567890"))
          .andExpect(jsonPath("$.participants[0].delivery.status").value("SHIPPING"))
          .andExpect(jsonPath("$.participants[1].status").value("AWAITING_PAYMENT"))
          .andExpect(jsonPath("$.participants[1].depositorName").value("레이팬"))
          .andExpect(jsonPath("$.participants[1].refundAccount").doesNotExist())
          .andExpect(jsonPath("$.participants[1].delivery").doesNotExist());
    }

    @Test
    void 호스트가_아니면_403을_반환한다() throws Exception {
      given(buncheolManagementQueryService.getManagement(10L, HOST_ID))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION));

      mockMvc
          .perform(get("/v1/buncheols/{id}/management", 10L).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }

    @Test
    void 존재하지_않는_분철이면_404를_반환한다() throws Exception {
      given(buncheolManagementQueryService.getManagement(10L, HOST_ID))
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      mockMvc
          .perform(get("/v1/buncheols/{id}/management", 10L).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_FOUND.getCode())));
    }
  }
}
