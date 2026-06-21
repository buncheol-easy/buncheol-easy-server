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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipateResult;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.user.domain.BankAccount;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @MockitoBean private ParticipationService participationService;

  @MockitoBean private ParticipationDetailQueryService participationDetailQueryService;

  @MockitoBean private MyParticipationQueryService myParticipationQueryService;

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
    Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
    ParticipateResult result =
        new ParticipateResult(
            500L, 53_000L, dueAt, BankAccount.of("국민은행", "98765432", "개최자"));
    given(participationService.participate(eq(10L), eq(PARTICIPANT_ID), any()))
        .willReturn(result);

    // when & then
    mockMvc
        .perform(
            post("/v1/buncheols/{buncheolId}/participations", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "buncheolMemberId": 200,
                      "shippingAddressId": 1,
                      "refundAccount": {
                        "bank": "국민은행",
                        "account": "12345678",
                        "holder": "홍길동"
                      }
                    }
                    """))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "participations-participate",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("분철 참여 신청")
                        .description(
                            """
                            멤버 슬롯을 선착순으로 점유한다. 점유에 성공하면 입금확인중(AWAITING_PAYMENT) 상태로 등록되고,
                            응답으로 개최자 계좌·입금 총액·입금 만료 시각(`dueAt`)을 받는다. 참여와 동시에 분철 취소 시
                            환불받을 본인 계좌(`refundAccount`)를 입력한다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 409 | `BCH-060` (`BUNCHEOL_NOT_RECRUITING`) | 모집 중인 분철이 아님 |
                            | 403 | `BCH-065` (`PARTICIPATION_HOST_CANNOT_PARTICIPATE`) | 개최자 본인 참여 |
                            | 409 | `BCH-070` (`PARTICIPATION_ALREADY_EXISTS`) | 해당 멤버 슬롯이 이미 점유됨 |
                            """)
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("ParticipateRequest"))
                        .requestFields(
                            fieldWithPath("buncheolMemberId").description("참여할 분철 멤버 슬롯 ID"),
                            fieldWithPath("shippingAddressId").description("수령지 ID"),
                            fieldWithPath("refundAccount").description("분철 취소 시 환불받을 본인 계좌"),
                            fieldWithPath("refundAccount.bank").description("은행명"),
                            fieldWithPath("refundAccount.account").description("계좌번호 (숫자만)"),
                            fieldWithPath("refundAccount.holder").description("예금주"))
                        .responseSchema(Schema.schema("ParticipateResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("참여 ID"),
                            fieldWithPath("amount").description("입금할 총액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("dueAt").description("입금 만료 시각 (UTC ISO-8601)"),
                            fieldWithPath("hostAccount").description("참여자가 입금할 개최자 계좌"),
                            fieldWithPath("hostAccount.bank").description("개최자 은행명"),
                            fieldWithPath("hostAccount.account").description("개최자 계좌번호"),
                            fieldWithPath("hostAccount.holder").description("개최자 예금주"))
                        .build())));
  }

  @Test
  void 내_참여_목록_조회() throws Exception {
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
            ParticipationStatus.CONFIRMED,
            null,
            BuncheolStatus.CONFIRMED,
            deadline,
            dueAt,
            confirmedAt);
    given(myParticipationQueryService.getMyParticipations(PARTICIPANT_ID))
        .willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/participations/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participations-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("내가 참여한 분철 목록 조회")
                        .description("마이페이지에서 사용자가 참여한 분철 목록을 최신 참여 순으로 조회한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("MyParticipationListResponse"))
                        .responseFields(
                            fieldWithPath("[].participationId").description("참여 ID"),
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].buncheolTitle").description("분철 제목"),
                            fieldWithPath("[].buncheolMemberCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].memberName").description("내가 참여한 멤버 이름"),
                            fieldWithPath("[].amount").description("참여 금액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("[].participationStatus")
                                .description(
                                    "내 참여 상태 (AWAITING_PAYMENT | CONFIRMED | CANCELLED)"),
                            fieldWithPath("[].cancelReason")
                                .description(
                                    "취소 사유 (PAYMENT_TIMEOUT | SELF_CANCELLED | BUNCHEOL_CANCELLED). 취소가 아니면 null")
                                .optional(),
                            fieldWithPath("[].buncheolStatus")
                                .description("분철 진행 상태 (RECRUITING | CONFIRMED | CANCELLED)"),
                            fieldWithPath("[].buncheolDeadline").description("분철 모집 마감일"),
                            fieldWithPath("[].dueAt")
                                .description("입금 만료 시각. 입금확인중(AWAITING_PAYMENT) 외에는 의미 없음")
                                .optional(),
                            fieldWithPath("[].confirmedAt")
                                .description("입금확인 시각. 미확인 시 null")
                                .optional())
                        .build())));
  }

  @Test
  void 참여_상세_조회() throws Exception {
    Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
    ParticipationDetailResponse detail =
        new ParticipationDetailResponse(
            500L,
            10L,
            "뉴진스 1집 분철",
            "민지",
            53_000L,
            ParticipationStatus.AWAITING_PAYMENT,
            null,
            dueAt,
            null,
            new HostAccountResponse("국민은행", "98765432", "개최자"));
    given(participationDetailQueryService.getDetail(PARTICIPANT_ID, 500L)).willReturn(detail);

    mockMvc
        .perform(
            get("/v1/participations/{participationId}", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participations-detail",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("참여 상세 조회")
                        .description(
                            "참여자 본인이 참여 상세를 조회한다. 입금확인중(AWAITING_PAYMENT) 단계에서만 입금할 개최자"
                                + " 계좌(`hostAccount`)·총액·만료 시각을 노출하며, 그 외 상태에서는 `hostAccount` 가 null 이다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("ParticipationDetailResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("참여 ID"),
                            fieldWithPath("buncheolId").description("분철 ID"),
                            fieldWithPath("buncheolTitle").description("분철 제목"),
                            fieldWithPath("memberName").description("참여한 멤버 이름").optional(),
                            fieldWithPath("amount").description("입금할 총액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("status")
                                .description("참여 상태 (AWAITING_PAYMENT | CONFIRMED | CANCELLED)"),
                            fieldWithPath("cancelReason")
                                .description("취소 사유. 취소가 아니면 null")
                                .optional(),
                            fieldWithPath("dueAt")
                                .description("입금 만료 시각 (UTC ISO-8601)")
                                .optional(),
                            fieldWithPath("confirmedAt")
                                .description("입금확인 시각. 미확인 시 null")
                                .optional(),
                            fieldWithPath("hostAccount")
                                .description("입금할 개최자 계좌. AWAITING_PAYMENT 외에는 null")
                                .optional(),
                            fieldWithPath("hostAccount.bank").description("개최자 은행명").optional(),
                            fieldWithPath("hostAccount.account")
                                .description("개최자 계좌번호")
                                .optional(),
                            fieldWithPath("hostAccount.holder").description("개최자 예금주").optional())
                        .build())));
  }

  @Test
  void 개최자_입금확인() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/confirm", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-confirm",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("개최자 수동 입금확인")
                        .description(
                            "개최자가 실제 입금을 확인해 입금확인중(AWAITING_PAYMENT) 참여를 CONFIRMED 로 전환한다. 입금 기한 내에만"
                                + " 가능하며, 개최자 본인만 호출할 수 있다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 분철_참여_취소() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/v1/participations/{participationId}", 500L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("분철 참여 취소")
                        .description("참여자 본인이 분철 참여를 취소한다. 입금확인중(AWAITING_PAYMENT) 단계에서만 취소가 가능하다.")
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
