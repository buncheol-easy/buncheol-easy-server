package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipateResult;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
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
import org.springframework.restdocs.payload.JsonFieldType;
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

                            **참여 코드 슬롯 (`saleStatus: "CODE_ONLY"`)**

                            상세 조회의 슬롯이 `CODE_ONLY` 면 `participationCode` 가 **필수**다. 반대로 선착순
                            슬롯에 코드를 보내면 거부한다(`BCH-096`) — 조용히 무시하면 오배정 문의를 사후에
                            재현할 수 없기 때문이다. 코드는 슬롯 1개에만 유효한 1회용이며 대소문자·하이픈·공백을
                            무시하고, 혼동 문자(`I`·`L`→`1`, `O`→`0`)를 교정해 대조한다.

                            코드 참여는 **결제 구간이 통째로 없다**:

                            - 슬롯 금액과 배송비가 모두 0원으로 처리되어 참여 즉시 확정(CONFIRMED)된다
                            - 응답의 `amount` 는 `0`, `dueAt`·`hostAccount` 는 `null` 이다 — **입금 안내 화면을 띄우면 안 된다**
                            - `refundAccount` 를 **생략해야 한다**. 환불할 금액이 없어 계좌를 받지 않으며,
                              입력란을 노출하면 존재하지 않는 보증금 조건이 있는 것처럼 보인다
                            - 이후 공유하는 것은 배송지·운송장 등 배송 기능뿐이다

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `buncheolMemberId` 등 필수값 누락 |
                            | 400 | `BCH-062` (`PARTICIPATION_REQUIRED_FIELD_MISSING`) | 참여 필수 항목 누락 (도메인 방어 검증 — 정상 HTTP 요청에서는 `C-001` 이 먼저 잡는다) |
                            | 400 | `BCH-065` (`PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED`) | 선택한 수령지의 배송방법을 이 분철이 지원하지 않음 |
                            | 400 | `USR-026` (`USER_BANK_ACCOUNT_FORMAT_INVALID`) | `refundAccount.account` 가 숫자·하이픈 형식이 아님 |
                            | 400 | `USR-034` (`USER_BANK_ACCOUNT_TOO_SHORT`) | `refundAccount.account` 가 하이픈 제외 8자리 미만 |
                            | 403 | `BCH-066` (`PARTICIPATION_HOST_CANNOT_PARTICIPATE`) | 개최자 본인 참여 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 404 | `BCH-061` (`PARTICIPATION_MEMBER_NOT_FOUND`) | 해당 분철에 존재하지 않는 멤버 슬롯 |
                            | 409 | `BCH-060` (`BUNCHEOL_NOT_RECRUITING`) | 모집 중인 분철이 아님 |
                            | 409 | `BCH-075` (`PARTICIPATION_ALREADY_JOINED_BUNCHEOL`) | 같은 분철에 이미 참여 중 (분철당 1회) |
                            | 409 | `BCH-070` (`PARTICIPATION_ALREADY_EXISTS`) | 해당 멤버 슬롯이 이미 점유됨 |
                            | 400 | `BCH-095` (`PARTICIPATION_CODE_REQUIRED`) | `CODE_ONLY` 슬롯인데 `participationCode` 누락 |
                            | 400 | `BCH-096` (`PARTICIPATION_CODE_NOT_APPLICABLE`) | 선착순 슬롯에 `participationCode` 를 보냄 |
                            | 400 | `BCH-097` (`PARTICIPATION_CODE_INVALID`) | 코드 형식 오류·미존재·**다른 슬롯에 발급된 코드**. 셋을 구분하지 않는다 — 타 슬롯임을 알리면 남의 코드를 받은 사람이 그 슬롯을 찾아가 점유한다 |
                            | 409 | `BCH-098` (`PARTICIPATION_CODE_EXPIRED`) | 유효기한이 지난 코드 |
                            | 409 | `BCH-099` (`PARTICIPATION_CODE_ALREADY_USED`) | 이미 사용된 코드 |
                            | 409 | `BCH-100` (`PARTICIPATION_CODE_REVOKED`) | 운영자가 폐기한 코드 (재발급으로 대체됨) |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestSchema(Schema.schema("ParticipateRequest"))
                        .requestFields(
                            fieldWithPath("buncheolMemberId")
                                .description("참여할 분철 멤버 슬롯 ID (단일 선택 정책)"),
                            fieldWithPath("shippingAddressId").description("수령지 ID"),
                            fieldWithPath("refundAccount")
                                .description(
                                    "분철 취소 시 환불받을 본인 계좌. **코드 참여(0원)에서는 생략** — 환불할 금액이 없다")
                                .optional(),
                            fieldWithPath("refundAccount.bank").description("은행명"),
                            fieldWithPath("refundAccount.account")
                                .description(
                                    "계좌번호 (숫자·하이픈). **하이픈을 제외한 숫자 8자리 이상** — 미만이면 `400 USR-034`"),
                            fieldWithPath("refundAccount.holder").description("예금주"),
                            fieldWithPath("participationCode")
                                .description(
                                    "참여 코드. `CODE_ONLY` 슬롯에서만 필수이고 선착순 슬롯에 보내면 `400 BCH-096`."
                                        + " 대소문자·하이픈·공백 무시")
                                .type(JsonFieldType.STRING)
                                .optional())
                        .responseSchema(Schema.schema("ParticipateResponse"))
                        .responseFields(
                            fieldWithPath("participationId").description("생성된 참여 ID"),
                            fieldWithPath("amount").description("입금할 총액 (멤버 가격 + 배송비, 원)"),
                            fieldWithPath("dueAt")
                                .description("입금 만료 시각 (UTC ISO-8601). 코드 참여는 결제가 없어 null")
                                .optional(),
                            fieldWithPath("hostAccount")
                                .description("참여자가 입금할 개최자 계좌. 코드 참여는 null")
                                .optional(),
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
            // CONFIRMED 라 hostAccount·refundHolder 둘 다 비노출
            null,
            new MyParticipationDeliveryResponse(
                900L,
                ShippingMethod.GS25_HALF,
                "GS25 강남점",
                "1234567890",
                DeliveryStatus.DELIVERED),
            new ShippingFeePaybackResponse(
                PaybackStatus.ELIGIBLE,
                Instant.parse("2026-06-12T13:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                new RefundAccountResponse("국민은행", "12345678", "홍길동")), FlowType.LEGACY, null, null, null,
            ParticipationCancellability.BLOCKED_BY_STATUS);
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
            "홍길동",
            null,
            new ShippingFeePaybackResponse(
                  PaybackStatus.NONE,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")), FlowType.LEGACY, null, null, null,
            ParticipationCancellability.FLOW_NOT_SUPPORTED);
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
                            fieldWithPath("[].refundHolder")
                                .description(
                                    "입금자명 안내용. 참여 시점에 박제된 환불계좌 예금주명이며 자동 입금확인이 이 이름으로 매칭한다."
                                        + " 입금확인중(AWAITING_PAYMENT)이 아니면 null")
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
                            fieldWithPath("[].payback.submitDeadline")
                                .description(
                                    "환급 신청 마감 시각 (배송 완료 시각 + 신청 가능 일수, UTC ISO-8601)."
                                        + " 이벤트 비대상이거나 배송 완료 전 등 마감 미적용이면 null."
                                        + " 신청 이력 상태(REQUESTED/COMPLETED 등)에서도 내려가므로"
                                        + " 마감 안내 표시 여부는 status 와 조합해 판단한다")
                                .optional(),
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
                                .description("예금주"),
                            fieldWithPath("[].flowType")
                                .description("분철 진행 방식 (LEGACY: 즉시 입금 | C2C: 신청→확정→입금 직거래)"),
                            fieldWithPath("[].paymentSentAt")
                                .description("C2C '보냈어요' 마킹 시각. 마킹 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("[].paymentRejectedAt")
                                .description(
                                    "C2C 개최자 반려('입금 못 찾음') 시각. status=AWAITING_PAYMENT 와 함께 오면 재확인이 필요한 상태다. "
                                        + "재마킹 시 null 로 초기화되며, 참여자 셀프 철회는 null")
                                .optional(),
                            fieldWithPath("[].openChatUrl")
                                .description("C2C 개최자 오픈채팅 링크. 미등록이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("[].cancellability")
                                .description(
                                    """
                                    자발 취소 가능 여부와 사유 (docs/56 S-1). 취소 API 게이트와 같은 판정이라 \
                                    화면은 이 값만 보고 취소 버튼을 노출하면 된다.
                                    CANCELLABLE: 취소 가능 |
                                    BLOCKED_BY_STATUS: 보냈어요·입금확인 이후 — 고객센터 문의 (BCH-086). \
                                    이미 취소된 참여(CANCELLED)도 이 값이며 안내 대상이 아니다 |
                                    FLOW_NOT_SUPPORTED: LEGACY 참여 (BCH-091) |
                                    BLOCKED_BY_HOST_CONFIRM: 개최자 성사 확정 후 — 개최자 연락 (BCH-092)"""))
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
                  null,
                  new RefundAccountResponse("국민은행", "12345678", "홍길동")), FlowType.LEGACY, null, null, null,
            ParticipationCancellability.FLOW_NOT_SUPPORTED);
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
                            fieldWithPath("payback.submitDeadline")
                                .description(
                                    "환급 신청 마감 시각 (배송 완료 시각 + 신청 가능 일수, UTC ISO-8601)."
                                        + " 이벤트 비대상이거나 배송 완료 전 등 마감 미적용이면 null."
                                        + " 신청 이력 상태(REQUESTED/COMPLETED 등)에서도 내려가므로"
                                        + " 마감 안내 표시 여부는 status 와 조합해 판단한다")
                                .optional(),
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
                            fieldWithPath("payback.refundAccount.holder").description("예금주"),
                            fieldWithPath("flowType")
                                .description("분철 진행 방식 (LEGACY: 즉시 입금 | C2C: 신청→확정→입금 직거래)"),
                            fieldWithPath("paymentSentAt")
                                .description("C2C '보냈어요' 마킹 시각. 마킹 전이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("paymentRejectedAt")
                                .description(
                                    "C2C 개최자 반려('입금 못 찾음') 시각. status=AWAITING_PAYMENT 와 함께 오면 재확인이 필요한 상태다. "
                                        + "재마킹 시 null 로 초기화되며, 참여자 셀프 철회는 null")
                                .optional(),
                            fieldWithPath("openChatUrl")
                                .description("C2C 개최자 오픈채팅 링크. 미등록이거나 LEGACY 면 null")
                                .optional(),
                            fieldWithPath("cancellability")
                                .description(
                                    """
                                    자발 취소 가능 여부와 사유 (docs/56 S-1). 취소 API 게이트와 같은 판정이라 \
                                    화면은 이 값만 보고 취소 버튼을 노출하면 된다.
                                    CANCELLABLE: 취소 가능 |
                                    BLOCKED_BY_STATUS: 보냈어요·입금확인 이후 — 고객센터 문의 (BCH-086) |
                                    FLOW_NOT_SUPPORTED: LEGACY 참여 (BCH-091) |
                                    BLOCKED_BY_HOST_CONFIRM: 개최자 성사 확정 후 — 개최자 연락 (BCH-092)"""))
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

  @Test
  void C2C_참여자_보냈어요_마킹() throws Exception {
    mockMvc
        .perform(post("/v1/participations/{participationId}/payment-sent", 500L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-sent",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("C2C 참여자 '보냈어요' 마킹")
                        .description(
                            """
                            입금 후 참여자가 입금 사실을 마킹한다 (`AWAITING_PAYMENT` → `PAYMENT_SENT`). 마킹된 참여는
                            입금 만료 대상에서 제외되고 개최자의 입금확인을 기다린다. 기한 경과 검사는 하지 않으며(기한 직전 입금 보호),
                            이미 마킹된 상태의 재요청은 멱등 성공한다. C2C 분철 전용이다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철의 참여 |
                            | 409 | `BCH-087` (`PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED`) | 입금 대기 상태가 아님 (만료·취소·확정됨) |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .build())));
  }

  @Test
  void C2C_참여자_보냈어요_철회() throws Exception {
    mockMvc
        .perform(
            delete("/v1/participations/{participationId}/payment-sent", 500L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-payment-sent-revert",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("C2C 참여자 '보냈어요' 철회")
                        .description(
                            "오마킹 셀프 수정. `PAYMENT_SENT` 를 `AWAITING_PAYMENT` 로 되돌린다(기한 유지). 이미 입금 대기로"
                                + " 돌아가 있으면 멱등 성공한다. C2C 분철 전용이다.")
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .build())));
  }

  @Test
  void C2C_개최자_미입금_반려() throws Exception {
    mockMvc
        .perform(
            post("/v1/participations/{participationId}/reject-payment", 500L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-reject-payment",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("C2C 개최자 미입금 반려")
                        .description(
                            """
                            개최자가 통장에서 입금 내역을 찾지 못한 '보냈어요' 를 반려한다. 취소가 아니라 `AWAITING_PAYMENT` 로
                            복귀시키고 입금 기한을 `max(기존 기한, 지금+24h)` 로 연장하며, 참여자에게 연장된 새 기한을 담은 입금
                            재확인 알림이 발송된다. C2C 분철 전용이며 개최자 본인만 호출할 수 있다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 호출자가 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철의 참여 |
                            | 409 | `BCH-087` (`PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED`) | '보냈어요' 상태가 아님 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .build())));
  }

  @Test
  void C2C_참여자_자발_취소() throws Exception {
    mockMvc
        .perform(delete("/v1/participations/{participationId}", 500L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participations-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Participation")
                        .summary("C2C 참여자 자발 취소")
                        .description(
                            """
                            참여자가 스스로 참여를 취소한다 (docs/46 §5 취소 3구간 + docs/56 H-09). 신청(`APPLIED`)과
                            **성사 확정을 거치지 않은** 입금 대기(`AWAITING_PAYMENT`)에서만 가능하고, '보냈어요' 이후는
                            돈이 개최자에게 간 구간이라 문의 경유로 안내된다. C2C 분철 전용이다(LEGACY 는 현행대로 취소 경로 없음).

                            개최자가 성사 확정을 누른 뒤에는 참여자가 스스로 빠질 수 없다(docs/56 H-09). 다만 입금
                            수집중 분철에 추가 모집(docs/46 §4.7-E1)으로 들어와 신청 즉시 `AWAITING_PAYMENT` 가 된
                            참여는 성사 확정을 거치지 않았으므로 계속 취소할 수 있다 — 그렇지 않으면 이 경로의 참여자는
                            신청하는 순간 입금 기한까지 잠긴다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 409 | `BCH-086` (`PARTICIPATION_CANCEL_NOT_ALLOWED`) | 취소 불가 구간 ('보냈어요'·입금확인 후 — 문의 경유). **상태 검사를 먼저 하므로 LEGACY 라도 확정된 참여는 이 코드다** |
                            | 409 | `BCH-091` (`PARTICIPATION_CANCEL_NOT_SUPPORTED`) | LEGACY 분철의 참여 (취소 경로 없음 — 기한 만료 자동 취소 안내) |
                            | 409 | `BCH-092` (`PARTICIPATION_CANCEL_AFTER_HOST_CONFIRM`) | 개최자 성사 확정을 거쳐 입금 대기가 된 참여 (개최자에게 연락 안내) |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("participationId").description("참여 ID"))
                        .build())));
  }
}
