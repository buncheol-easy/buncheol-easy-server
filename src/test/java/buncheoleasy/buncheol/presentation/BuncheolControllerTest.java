package buncheoleasy.buncheol.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberBidResponse;
import buncheoleasy.buncheol.dto.response.MyBidResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
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

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

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
                  "gs25ShippingFee": 3000,
                  "buncheolMembers": [
                    {
                      "memberId": 200,
                      "bidMinPrice": 50000
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
                  "keepImageIds": [1, 2]
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
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "gs25ShippingFee": 3000,
                      "buncheolMembers": [
                        {"memberId": 200, "bidMinPrice": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

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
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "buncheolMembers": [
                        {"memberId": 200, "bidMinPrice": 50000}
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
          .perform(multipart("/v1/buncheols").file(requestPart).with(mockAuth()))
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
                      "gs25ShippingFee": 3000,
                      "buncheolMembers": [
                        {"memberId": 200, "bidMinPrice": 50000}
                      ]
                    }
                    """
              .formatted(deadline);

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
      Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
      String invalidJson =
          """
                    {
                      "groupId": 100,
                      "title": "제목",
                      "purchaseSite": "공식 스토어",
                      "deadline": "%s",
                      "gs25ShippingFee": 3000,
                      "buncheolMembers": [
                        {"memberId": 200, "bidMinPrice": 50000}
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
  @DisplayName("분철 수동 마감 테스트")
  class CloseBuncheolTest {

    @Test
    void 수동_마감에_성공하면_204를_반환한다() throws Exception {
      // when & then
      mockMvc
          .perform(post("/v1/buncheols/{id}/close", 10L).with(mockAuth()))
          .andExpect(status().isNoContent());

      then(buncheolService).should().closeBuncheol(HOST_ID, 10L);
    }

    @Test
    void 분철이_없으면_404를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND))
          .given(buncheolService)
          .closeBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(post("/v1/buncheols/{id}/close", 10L).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_FOUND.getCode())));
    }

    @Test
    void 소유자가_아니면_403을_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheolService)
          .closeBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(post("/v1/buncheols/{id}/close", 10L).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }

    @Test
    void 모집중이_아니면_409를_반환한다() throws Exception {
      // given
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheolService)
          .closeBuncheol(HOST_ID, 10L);

      // when & then
      mockMvc
          .perform(post("/v1/buncheols/{id}/close", 10L).with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(content().string(containsString(ErrorCode.BUNCHEOL_NOT_RECRUITING.getCode())));
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
              10L, "뉴진스 1집 분철", "뉴진스", BuncheolStatus.RECRUITING, deadline, 5, 7L, createdAt);
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
          .andExpect(jsonPath("$[0].activeParticipationCount").value(7));
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
              List.of("https://cdn/img1.jpg"),
              List.of(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000)),
              List.of(
                  new BuncheolMemberBidResponse(
                      101L,
                      1001L,
                      "민지",
                      "minji.png",
                      40_000L,
                      List.of(90_000L, 70_000L, 50_000L),
                      4)),
              true,
              new MyParticipationSummaryResponse(
                  1, List.of(new MyBidResponse(601L, 101L, 50_000L, 3))));
      given(buncheolDetailQueryService.getDetail(10L, HOST_ID)).willReturn(response);

      mockMvc
          .perform(get("/v1/buncheols/{id}", 10L).with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.groupName").value("뉴진스"))
          .andExpect(jsonPath("$.status").value("RECRUITING"))
          .andExpect(jsonPath("$.hostedByMe").value(true))
          .andExpect(jsonPath("$.imageUrls[0]").value("https://cdn/img1.jpg"))
          .andExpect(jsonPath("$.shippingOptions[0].method").value("GS25_HALF"))
          .andExpect(jsonPath("$.shippingOptions[0].fee").value(3000))
          .andExpect(jsonPath("$.members[0].buncheolMemberId").value(101))
          .andExpect(jsonPath("$.members[0].bidMinPrice").value(40000))
          .andExpect(jsonPath("$.members[0].topBidAmounts[0]").value(90000))
          .andExpect(jsonPath("$.members[0].activeParticipantCount").value(4))
          .andExpect(jsonPath("$.myParticipation.participatedMemberCount").value(1))
          .andExpect(jsonPath("$.myParticipation.bids[0].participationId").value(601))
          .andExpect(jsonPath("$.myParticipation.bids[0].rank").value(3));
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
              List.of(),
              List.of(new ShippingOptionResponse(ShippingMethod.CU_HALF, 4000)),
              List.of(),
              false,
              null);
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
              List.of(),
              List.of(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 3000)),
              List.of(),
              false,
              null);
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
}
