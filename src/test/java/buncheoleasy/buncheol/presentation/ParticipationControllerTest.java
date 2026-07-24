package buncheoleasy.buncheol.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipateResult;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.buncheol.dto.request.ShippingFeePaybackRequest;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationDeliveryResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.buncheol.dto.response.ShippingFeePaybackResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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

  @MockitoBean private ParticipationService participationService;

  @MockitoBean private ParticipationDetailQueryService participationDetailQueryService;

  @MockitoBean private MyParticipationQueryService myParticipationQueryService;

  @MockitoBean private ShippingFeePaybackService shippingFeePaybackService;

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
        "refundAccount": {
          "bank": "국민은행",
          "account": "12345678",
          "holder": "홍길동"
        }
      }
      """;

  @Nested
  @DisplayName("분철 참여 신청 API 테스트")
  class ParticipateTest {

    @Test
    void 참여_신청에_성공하면_201을_반환한다() throws Exception {
      Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
      ParticipateResult result =
          new ParticipateResult(
              PARTICIPATION_ID, 53_000L, dueAt, BankAccount.of("국민은행", "98765432", "개최자"));

      given(
              participationService.participate(
                  eq(BUNCHEOL_ID), eq(PARTICIPANT_ID), any(ParticipateRequest.class)))
          .willReturn(result);

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(VALID_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.participationId").value(PARTICIPATION_ID))
          .andExpect(jsonPath("$.amount").value(53_000))
          .andExpect(jsonPath("$.hostAccount.bank").value("국민은행"))
          .andExpect(jsonPath("$.hostAccount.account").value("98765432"))
          .andExpect(jsonPath("$.hostAccount.holder").value("개최자"));
    }

    @Test
    void 멤버_슬롯을_지정하지_않으면_400을_반환한다() throws Exception {
      // 참여 1건 = 멤버 슬롯 1개(단일 선택 정책). buncheolMemberId 는 @NotNull 검증에서 걸러진다.
      String missingMemberJson =
          """
          {
            "shippingAddressId": 200,
            "refundAccount": {
              "bank": "국민은행",
              "account": "12345678",
              "holder": "홍길동"
            }
          }
          """;

      mockMvc
          .perform(
              post("/v1/buncheols/{buncheolId}/participations", BUNCHEOL_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(missingMemberJson)
                  .with(mockAuth()))
          .andExpect(status().isBadRequest());

      then(participationService).shouldHaveNoInteractions();
    }

    @Test
    void 모집중이_아닌_분철이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(participationService)
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
          .given(participationService)
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
    void 같은_분철에_이미_참여_중이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_ALREADY_JOINED_BUNCHEOL))
          .given(participationService)
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
                      Matchers.containsString(
                          ErrorCode.PARTICIPATION_ALREADY_JOINED_BUNCHEOL.getCode())));
    }

    @Test
    void 이미_활성_참여가_있으면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS))
          .given(participationService)
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
  @DisplayName("내 참여 목록 조회 API 테스트")
  class GetMyParticipationsTest {

    @Test
    void 참여_목록을_200으로_반환한다() throws Exception {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
      Instant confirmedAt = Instant.parse("2026-06-01T13:00:00Z");
      MyParticipationResponse response =
          new MyParticipationResponse(
              500L,
              10L,
              "뉴진스 1집 분철",
              5,
              "민지",
              53_000L,
              3_000L,
              ParticipationStatus.CONFIRMED,
              null,
              BuncheolStatus.CONFIRMED,
              deadline,
              dueAt,
              confirmedAt,
              "https://cdn.example.com/buncheols/10/main.jpg",
              List.of(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 1_800)),
              null,
              new MyParticipationDeliveryResponse(
                  900L, ShippingMethod.GS25_HALF, "GS25 강남점", "1234567890", DeliveryStatus.SHIPPING),
              new ShippingFeePaybackResponse(
                  PaybackStatus.NONE,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")));

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
          .andExpect(jsonPath("$[0].amount").value(53_000))
          .andExpect(jsonPath("$[0].shippingFee").value(3_000))
          .andExpect(jsonPath("$[0].participationStatus").value("CONFIRMED"))
          .andExpect(jsonPath("$[0].buncheolStatus").value("CONFIRMED"))
          .andExpect(jsonPath("$[0].confirmedAt").value("2026-06-01T13:00:00Z"))
          .andExpect(
              jsonPath("$[0].thumbnailUrl").value("https://cdn.example.com/buncheols/10/main.jpg"))
          .andExpect(jsonPath("$[0].shippingOptions[0].method").value("GS25_HALF"))
          .andExpect(jsonPath("$[0].shippingOptions[0].fee").value(1_800))
          .andExpect(jsonPath("$[0].hostAccount").doesNotExist())
          .andExpect(jsonPath("$[0].delivery.deliveryId").value(900))
          .andExpect(jsonPath("$[0].delivery.status").value("SHIPPING"))
          .andExpect(jsonPath("$[0].delivery.trackingNumber").value("1234567890"))
          .andExpect(jsonPath("$[0].payback.status").value("NONE"));
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
  @DisplayName("참여 상세 조회 API 테스트")
  class GetParticipationDetailTest {

    @Test
    void 입금확인중_참여는_개최자_계좌를_포함해_200으로_반환한다() throws Exception {
      Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
      ParticipationDetailResponse response =
          new ParticipationDetailResponse(
              PARTICIPATION_ID,
              10L,
              "뉴진스 1집 분철",
              "민지",
              53_000L,
              ParticipationStatus.AWAITING_PAYMENT,
              null,
              dueAt,
              null,
              new HostAccountResponse("국민은행", "98765432", "개최자"),
              new ShippingFeePaybackResponse(
                  PaybackStatus.NONE,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")));

      given(participationDetailQueryService.getDetail(PARTICIPANT_ID, PARTICIPATION_ID))
          .willReturn(response);

      mockMvc
          .perform(get("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.participationId").value(PARTICIPATION_ID))
          .andExpect(jsonPath("$.buncheolTitle").value("뉴진스 1집 분철"))
          .andExpect(jsonPath("$.memberName").value("민지"))
          .andExpect(jsonPath("$.amount").value(53_000))
          .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
          .andExpect(jsonPath("$.hostAccount.bank").value("국민은행"))
          .andExpect(jsonPath("$.hostAccount.account").value("98765432"));
    }

    @Test
    void 참여가_존재하지_않으면_404를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND))
          .given(participationDetailQueryService)
          .getDetail(PARTICIPANT_ID, PARTICIPATION_ID);

      mockMvc
          .perform(get("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PARTICIPATION_NOT_FOUND.getCode())));
    }

    @Test
    void 참여자가_아니면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION))
          .given(participationDetailQueryService)
          .getDetail(PARTICIPANT_ID, PARTICIPATION_ID);

      mockMvc
          .perform(get("/v1/participations/{participationId}", PARTICIPATION_ID).with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(ErrorCode.PARTICIPATION_NO_PERMISSION.getCode())));
    }
  }

  @Nested
  @DisplayName("배송비 환급 신청 API 테스트")
  class RequestShippingFeePaybackTest {

    private static final String PAYBACK_REQUEST_BODY =
        """
        { "tweetUrl": "https://x.com/fan/status/1234567890" }
        """;

    @Test
    void 환급_신청에_성공하면_204를_반환한다() throws Exception {
      mockMvc
          .perform(
              post("/v1/participations/{participationId}/shipping-fee-payback", PARTICIPATION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(PAYBACK_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isNoContent());

      then(shippingFeePaybackService)
          .should()
          .request(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID), any(ShippingFeePaybackRequest.class));
    }

    @Test
    void 트윗_URL_이_비어있으면_400을_반환한다() throws Exception {
      mockMvc
          .perform(
              post("/v1/participations/{participationId}/shipping-fee-payback", PARTICIPATION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{ \"tweetUrl\": \"\" }")
                  .with(mockAuth()))
          .andExpect(status().isBadRequest());
    }

    @Test
    void 환급_대상이_아니면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PAYBACK_NOT_ELIGIBLE))
          .given(shippingFeePaybackService)
          .request(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID), any(ShippingFeePaybackRequest.class));

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/shipping-fee-payback", PARTICIPATION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(PAYBACK_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content().string(Matchers.containsString(ErrorCode.PAYBACK_NOT_ELIGIBLE.getCode())));
    }

    @Test
    void 이미_사용된_트윗_URL_이면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE))
          .given(shippingFeePaybackService)
          .request(eq(PARTICIPANT_ID), eq(PARTICIPATION_ID), any(ShippingFeePaybackRequest.class));

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/shipping-fee-payback", PARTICIPATION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(PAYBACK_REQUEST_BODY)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE.getCode())));
    }
  }

  @Nested
  @DisplayName("개최자 입금확인 API 테스트")
  class ConfirmPaymentTest {

    @Test
    void 입금확인에_성공하면_204를_반환한다() throws Exception {
      mockMvc
          .perform(
              post("/v1/participations/{participationId}/confirm", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isNoContent());

      then(participationService).should().confirmPayment(PARTICIPANT_ID, PARTICIPATION_ID);
    }

    @Test
    void 입금_기한이_지났으면_409를_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED))
          .given(participationService)
          .confirmPayment(PARTICIPANT_ID, PARTICIPATION_ID);

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/confirm", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isConflict())
          .andExpect(
              content()
                  .string(
                      Matchers.containsString(
                          ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED.getCode())));
    }

    @Test
    void 개최자가_아니면_403을_반환한다() throws Exception {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(participationService)
          .confirmPayment(PARTICIPANT_ID, PARTICIPATION_ID);

      mockMvc
          .perform(
              post("/v1/participations/{participationId}/confirm", PARTICIPATION_ID)
                  .with(mockAuth()))
          .andExpect(status().isForbidden())
          .andExpect(
              content()
                  .string(Matchers.containsString(ErrorCode.BUNCHEOL_NO_PERMISSION.getCode())));
    }
  }

}
