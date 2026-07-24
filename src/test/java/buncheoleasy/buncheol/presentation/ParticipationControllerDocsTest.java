package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipateResult;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationDeliveryResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.buncheol.dto.response.ShippingFeePaybackResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("Participation 관련 컨트롤러 문서화 테스트")
class ParticipationControllerDocsTest extends DocsTestSupport {

  private static final Long PARTICIPANT_ID = USER_ID;

  @MockitoBean private ParticipationService participationService;

  @MockitoBean private ParticipationDetailQueryService participationDetailQueryService;

  @MockitoBean private MyParticipationQueryService myParticipationQueryService;

  @MockitoBean private ShippingFeePaybackService shippingFeePaybackService;

  @Test
  void 분철_참여() throws Exception {
    // given
    Instant dueAt = Instant.parse("2026-06-02T12:00:00Z");
    ParticipateResult result =
        new ParticipateResult(500L, 53_000L, dueAt, BankAccount.of("국민은행", "98765432", "개최자"));
    given(participationService.participate(eq(10L), eq(PARTICIPANT_ID), any()))
        .willReturn(result);

    // when & then
    mockMvc
        .perform(
            post("/v1/buncheols/{buncheolId}/participations", 10L)
                .with(userAuth())
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
                            멤버 슬롯을 선착순으로 점유한다. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이고,
                            오픈 이벤트 운영 정책으로 **분철당 참여는 1회(멤버 1명)로 제한**된다 — 같은 분철에
                            활성(입금확인중·확정) 참여가 있으면 중복 참여가 거부되며, 취소·만료된 참여는 재참여할 수
                            있다. `buncheolMemberId` 로 슬롯 하나를 지정하고, 점유에 성공하면
                            입금확인중(AWAITING_PAYMENT) 상태로 등록되며 응답으로 생성된 참여
                            ID(`participationId`)·개최자 계좌·입금 총액·입금 만료 시각(`dueAt`)을 받는다.
                            `amount` 는 (멤버 가격 + 배송비)다. 참여와 동시에 분철 취소 시 환불받을 본인
                            계좌(`refundAccount`)를 입력한다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `buncheolMemberId` 등 필수값 누락 |
                            | 400 | `BCH-062` (`PARTICIPATION_REQUIRED_FIELD_MISSING`) | 참여 필수 항목 누락 (도메인 방어 검증 — 정상 HTTP 요청에서는 `C-001` 이 먼저 잡는다) |
                            | 400 | `BCH-065` (`PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED`) | 선택한 수령지의 배송방법을 이 분철이 지원하지 않음 |
                            | 403 | `BCH-066` (`PARTICIPATION_HOST_CANNOT_PARTICIPATE`) | 개최자 본인 참여 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 404 | `BCH-061` (`PARTICIPATION_MEMBER_NOT_FOUND`) | 해당 분철에 존재하지 않는 멤버 슬롯 |
                            | 409 | `BCH-060` (`BUNCHEOL_NOT_RECRUITING`) | 모집 중인 분철이 아님 |
                            | 409 | `BCH-075` (`PARTICIPATION_ALREADY_JOINED_BUNCHEOL`) | 같은 분철에 이미 참여 중 (분철당 1회) |
                            | 409 | `BCH-070` (`PARTICIPATION_ALREADY_EXISTS`) | 해당 멤버 슬롯이 이미 점유됨 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestSchema(Schema.schema("ParticipateRequest"))
                        .requestFields(
                            fieldWithPath("buncheolMemberId")
                                .description("참여할 분철 멤버 슬롯 ID (단일 선택 정책)"),
                            fieldWithPath("shippingAddressId").description("수령지 ID"),
                            fieldWithPath("refundAccount").description("분철 취소 시 환불받을 본인 계좌"),
                            fieldWithPath("refundAccount.bank").description("은행명"),
                            fieldWithPath("refundAccount.account").description("계좌번호 (숫자·하이픈)"),
                            fieldWithPath("refundAccount.holder").description("예금주"))
                        .responseSchema(Schema.schema("ParticipateResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("생성된 참여 ID"),
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
    MyParticipationResponse confirmed =
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
                PaybackStatus.ELIGIBLE,
                null,
                null,
                null,
                null,
                null,
                new RefundAccountResponse("국민은행", "12345678", "홍길동")));
    MyParticipationResponse awaitingPayment =
        new MyParticipationResponse(
            501L,
            20L,
            "에스파 시즌그리팅 분철",
            4,
            "카리나",
            41_000L,
            2_000L,
            ParticipationStatus.AWAITING_PAYMENT,
            null,
            BuncheolStatus.RECRUITING,
            deadline,
            dueAt,
            null,
            "https://cdn.example.com/buncheols/20/main.jpg",
            List.of(new ShippingOptionResponse(ShippingMethod.CU_HALF, 2_000)),
            new HostAccountResponse("국민은행", "98765432", "개최자"),
            null,
            new ShippingFeePaybackResponse(
                  PaybackStatus.NONE,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")));
    given(myParticipationQueryService.getMyParticipations(PARTICIPANT_ID))
        .willReturn(List.of(confirmed, awaitingPayment));

    mockMvc
        .perform(get("/v1/participations/me").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participations-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("내가 참여한 분철 목록 조회")
                        .description("마이페이지에서 사용자가 참여한 분철 목록을 최신 참여 순으로 조회한다.")
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("MyParticipationListResponse"))
                        .responseFields(
                            fieldWithPath("[].participationId").description("참여 ID"),
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].buncheolTitle").description("분철 제목"),
                            fieldWithPath("[].buncheolMemberCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].memberName").description("내가 참여한 멤버 이름"),
                            fieldWithPath("[].amount").description("참여 금액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("[].shippingFee")
                                .description("이 참여에 부과된 배송비 (원). 멤버 가격 = amount - shippingFee"),
                            fieldWithPath("[].participationStatus")
                                .description(
                                    "내 참여 상태 (AWAITING_PAYMENT | CONFIRMED | CANCELLED)"),
                            fieldWithPath("[].cancelReason")
                                .description(
                                    "취소 사유 (PAYMENT_TIMEOUT | BUNCHEOL_CANCELLED). 취소가 아니면 null")
                                .optional(),
                            fieldWithPath("[].buncheolStatus")
                                .description("분철 진행 상태 (RECRUITING | CONFIRMED | CANCELLED)"),
                            fieldWithPath("[].buncheolDeadline").description("분철 모집 마감일"),
                            fieldWithPath("[].dueAt")
                                .description("입금 만료 시각. 입금확인중(AWAITING_PAYMENT) 외에는 의미 없음")
                                .optional(),
                            fieldWithPath("[].confirmedAt")
                                .description("입금확인 시각. 미확인 시 null")
                                .optional(),
                            fieldWithPath("[].thumbnailUrl")
                                .description("분철 대표 이미지 URL. 이미지가 없으면 null")
                                .optional(),
                            fieldWithPath("[].shippingOptions")
                                .description("분철이 지원하는 배송방법·배송비 목록"),
                            fieldWithPath("[].shippingOptions[].method")
                                .description("배송방법 (GS25_HALF | CU_HALF)"),
                            fieldWithPath("[].shippingOptions[].fee").description("배송비 (원)"),
                            fieldWithPath("[].hostAccount")
                                .description(
                                    "입금할 개최자 계좌. 입금확인중(AWAITING_PAYMENT)이 아니거나 개최자 계좌 미등록이면 null")
                                .optional(),
                            fieldWithPath("[].hostAccount.bank").description("개최자 은행명").optional(),
                            fieldWithPath("[].hostAccount.account")
                                .description("개최자 계좌번호")
                                .optional(),
                            fieldWithPath("[].hostAccount.holder")
                                .description("개최자 예금주")
                                .optional(),
                            fieldWithPath("[].delivery")
                                .description("배송 스냅샷. 입금확인 시 생성되며 그 전에는 null")
                                .optional(),
                            fieldWithPath("[].delivery.deliveryId").description("배송 ID").optional(),
                            fieldWithPath("[].delivery.shippingMethod")
                                .description("마감 시점에 확정된 배송방법 스냅샷")
                                .optional(),
                            fieldWithPath("[].delivery.storeName")
                                .description("마감 시점에 확정된 편의점 지점명 스냅샷")
                                .optional(),
                            fieldWithPath("[].delivery.trackingNumber")
                                .description("운송장 번호. 등록 전에는 null")
                                .optional(),
                            fieldWithPath("[].delivery.status")
                                .description(
                                    "배송 상태 (SNAPSHOTTED | SHIPPING | DELIVERED | RECEIVED)")
                                .optional(),
                            fieldWithPath("[].payback")
                                .description("오픈 이벤트 배송비 환급(배송비 돌려받기) 상태. 비대상이어도 항상 내려간다"),
                            fieldWithPath("[].payback.status")
                                .description(
                                    "환급 상태 (NONE | ELIGIBLE | REQUESTED | COMPLETED | REJECTED |"
                                        + " EXPIRED). ELIGIBLE/EXPIRED 는 조회 시점 파생값"),
                            fieldWithPath("[].payback.tweetUrl")
                                .description("신청 시 제출한 후기 트윗 URL. 신청 전에는 null")
                                .optional(),
                            fieldWithPath("[].payback.requestedAt")
                                .description("환급 신청 시각. 신청 전에는 null")
                                .optional(),
                            fieldWithPath("[].payback.completedAt")
                                .description("환급 입금 완료 시각. 완료 전에는 null")
                                .optional(),
                            fieldWithPath("[].payback.rejectReason")
                                .description("반려 사유. REJECTED 외에는 null")
                                .optional(),
                            fieldWithPath("[].payback.amount")
                                .description("환급액 (신청 시점 배송비 스냅샷, 원). 신청 전에는 null")
                                .optional(),
                            fieldWithPath("[].payback.refundAccount")
                                .description("환급 입금받을 계좌 (참여 시 등록한 환불계좌)"),
                            fieldWithPath("[].payback.refundAccount.bank").description("은행명"),
                            fieldWithPath("[].payback.refundAccount.account")
                                .description("계좌번호"),
                            fieldWithPath("[].payback.refundAccount.holder")
                                .description("예금주"))
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
            new HostAccountResponse("국민은행", "98765432", "개최자"),
            new ShippingFeePaybackResponse(
                  PaybackStatus.NONE,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")));
    given(participationDetailQueryService.getDetail(PARTICIPANT_ID, 500L)).willReturn(detail);

    mockMvc
        .perform(
            get("/v1/participations/{participationId}", 500L).with(userAuth()))
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
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
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
                            fieldWithPath("hostAccount.holder").description("개최자 예금주").optional(),
                            fieldWithPath("payback")
                                .description("오픈 이벤트 배송비 환급(배송비 돌려받기) 상태. 비대상이어도 항상 내려간다"),
                            fieldWithPath("payback.status")
                                .description(
                                    "환급 상태 (NONE | ELIGIBLE | REQUESTED | COMPLETED | REJECTED |"
                                        + " EXPIRED). ELIGIBLE/EXPIRED 는 조회 시점 파생값"),
                            fieldWithPath("payback.tweetUrl")
                                .description("신청 시 제출한 후기 트윗 URL. 신청 전에는 null")
                                .optional(),
                            fieldWithPath("payback.requestedAt")
                                .description("환급 신청 시각. 신청 전에는 null")
                                .optional(),
                            fieldWithPath("payback.completedAt")
                                .description("환급 입금 완료 시각. 완료 전에는 null")
                                .optional(),
                            fieldWithPath("payback.rejectReason")
                                .description("반려 사유. REJECTED 외에는 null")
                                .optional(),
                            fieldWithPath("payback.amount")
                                .description("환급액 (신청 시점 배송비 스냅샷, 원). 신청 전에는 null")
                                .optional(),
                            fieldWithPath("payback.refundAccount")
                                .description("환급 입금받을 계좌 (참여 시 등록한 환불계좌)"),
                            fieldWithPath("payback.refundAccount.bank").description("은행명"),
                            fieldWithPath("payback.refundAccount.account").description("계좌번호"),
                            fieldWithPath("payback.refundAccount.holder").description("예금주"))
                        .build())));
  }

  @Test
  void 배송비_환급_신청() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/shipping-fee-payback", 500L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"tweetUrl\": \"https://x.com/fan/status/1234567890?s=20\" }")
                .with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-shipping-fee-payback",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("배송비 환급(배송비 돌려받기) 신청")
                        .description(
                            """
                            오픈 이벤트 분철(0원 슬롯) 참여의 배송 완료 후, X(트위터) 후기 트윗 URL 을 제출해 배송비 환급을 신청한다.
                            반려(REJECTED)된 신청의 재신청, 검수 전(REQUESTED) 잘못 올린 링크의 수정도 같은 엔드포인트를 사용한다 —
                            재신청 시 이전 반려 사유는 초기화되고, 제출할 때마다 운영자 슬랙 알림이 다시 발송된다.
                            제출한 URL 은 쿼리스트링을 제거한 퍼머링크로 정규화해 저장한다.

                            | 상태 | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `BCH-078` (`PAYBACK_TWEET_URL_INVALID`) | 트윗 퍼머링크 형식이 아님 |
                            | 403 | `BCH-069` (`PARTICIPATION_NO_PERMISSION`) | 참여자 본인이 아님 |
                            | 409 | `BCH-076` (`PAYBACK_NOT_ELIGIBLE`) | 환급 대상 아님 (비이벤트 분철·배송 완료 전·신청 마감) |
                            | 409 | `BCH-077` (`PAYBACK_STATE_TRANSITION_INVALID`) | 이미 입금 완료(COMPLETED)된 건 |
                            | 409 | `BCH-079` (`PAYBACK_TWEET_URL_DUPLICATE`) | 다른 참여의 환급 신청에 이미 사용된 트윗 URL |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .requestSchema(Schema.schema("ShippingFeePaybackRequest"))
                        .requestFields(
                            fieldWithPath("tweetUrl")
                                .description(
                                    "후기 트윗 URL (https://x.com/{handle}/status/{id} 또는"
                                        + " twitter.com. 쿼리스트링 허용)"))
                        .build())));
  }

  @Test
  void 개최자_입금확인() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/confirm", 500L).with(userAuth()))
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
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .build())));
  }
}
