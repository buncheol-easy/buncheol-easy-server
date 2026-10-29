package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.participation.BundleConfirmResult;
import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * ⚠️ 이 파일이 없으면 신설 엔드포인트가 <b>OpenAPI 명세서에 아예 나타나지 않는다</b> — {@code openapi3 →
 * copyApiDocs → bootJar} 체인이 DocsTest 스니펫에서 명세를 만들어 앱이 서빙하기 때문이다.
 */
@DisplayName("ParticipationBundle 컨트롤러 문서화 테스트")
class ParticipationBundleControllerDocsTest extends DocsTestSupport {

  private static final Long BUNDLE_ID = 141L;

  @MockitoBean private ParticipationBundleService participationBundleService;

  @Test
  void 참여자_묶음_보냈어요_마킹() throws Exception {
    given(participationBundleService.markPaymentSent(eq(USER_ID), eq(BUNDLE_ID)))
        .willReturn(List.of(232L, 233L));

    mockMvc
        .perform(
            post("/v1/participation-bundles/{bundleId}/payment-sent", BUNDLE_ID).with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participation-bundles-payment-sent",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("참여자 묶음 「보냈어요」 마킹")
                        .description(
                            """
                            참여자가 묶음의 입금 대기 슬롯을 **한 번에** 「보냈어요」로 표시한다
                            (`AWAITING_PAYMENT` → `PAYMENT_SENT`).

                            묶음은 **이체 1회**의 단위다. 슬롯마다 누르게 하면 한 번 보낸 돈을 여러 번 신고하게
                            되고, 중간에 멈추면 같은 묶음의 슬롯 상태가 갈린다 — 「제외」·입금확인이 모두
                            "묶음 안 슬롯은 갈리지 않는다" 를 전제로 서 있다.

                            **입금 기한이 지난 뒤에도 열려 있다.** 늦게 보낸 사람도 보냈다는 사실을 남길 수
                            있어야 개최자가 확인한다. 이미 마킹된 묶음의 재요청은 **멱등 성공**한다.

                            개최자에게는 **묶음 1통**으로 알림이 간다(멤버명 나열·금액 합산).

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BCH-069` (`PARTICIPATION_NO_PERMISSION`) | 본인 묶음이 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철 |
                            | 409 | `BCH-087` (`PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED`) | 마킹할 입금 대기 슬롯이 없음 — **개최자가 이미 전부 입금확인한 뒤** 누른 경우도 여기 해당한다 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .responseFields(
                            fieldWithPath("bundleId")
                                .type(JsonFieldType.NUMBER)
                                .description("마킹한 묶음 ID"),
                            fieldWithPath("markedParticipationIds")
                                .type(JsonFieldType.ARRAY)
                                .description("실제로 마킹된 참여 ID 목록"))
                        .responseSchema(Schema.schema("BundlePaymentSentResponse"))
                        .build())));
  }

  @Test
  void 개최자_묶음_입금확인() throws Exception {
    given(participationBundleService.confirmPayment(eq(USER_ID), eq(BUNDLE_ID), any()))
        .willReturn(new BundleConfirmResult(List.of(232L, 233L), true));

    mockMvc
        .perform(
            post("/v1/participation-bundles/{bundleId}/confirm", BUNDLE_ID)
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedSlotIds\":[232,233]}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participation-bundles-confirm",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("개최자 묶음 입금확인 (all-or-nothing)")
                        .description(
                            """
                            개최자가 묶음의 확인 가능 슬롯(`AWAITING_PAYMENT`·`PAYMENT_SENT`)을 **한 번에**
                            확정한다.

                            **부분 확인은 애초에 성립하지 않는다** — 이 API 에는 금액이 없어 시스템은 실입금액을
                            모르고, 개최자가 판단하는 것은 "이 이체가 들어왔는가" 하나뿐이다. 묶음은 이체 1회의
                            단위이므로 확인도 1회다.

                            🔴 **`expectedSlotIds` 는 필수다.** 개최자가 화면에서 본 슬롯 집합을 그대로 실어
                            보낸다. 서버의 실제 집합과 다르면 **409** 로 막고 새로고침을 유도한다 — 개최자가 **보지
                            못한 슬롯까지 확정**되면 안 된다. 순서는 보지 않고 집합으로 대조한다.

                            **입금 기한이 지나도 확인할 수 있다** — 개최자 확인이 늦어도 유효해야 한다.

                            참여자에게는 **묶음 1통**으로 확인 알림이 간다.

                            **이 묶음으로 분철의 미확인 참여가 0 이 되면 같은 요청에서 분철을 진행 확정한다** — 확인된
                            묶음마다 배송이 생기고(운송장 입력칸이 열림) 확인된 참여자에게 진행확정 알림이 간다. 응답의
                            `buncheolConfirmed` 가 `true` 다. 진행 확정에 실패하면(배송지 없음 등) 이 입금 확인도 함께
                            되돌린다. 미확인이 남아 있으면 진행 확정은 입금 기한 뒤 개최자가 한다
                            (`POST /v1/buncheols/{id}/confirm`).

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철 |
                            | 409 | `BCH-115` (`BUNDLE_SLOTS_CHANGED`) | 화면이 본 슬롯 집합과 다름 |
                            | 409 | `BCH-116` (`BUNDLE_CONFIRM_NOT_ALLOWED`) | 확인할 슬롯이 없음 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .requestFields(
                            fieldWithPath("expectedSlotIds")
                                .type(JsonFieldType.ARRAY)
                                .description("화면이 본 확인 대상 참여 ID 목록 (필수)"))
                        .responseFields(
                            fieldWithPath("bundleId")
                                .type(JsonFieldType.NUMBER)
                                .description("확인한 묶음 ID"),
                            fieldWithPath("confirmedParticipationIds")
                                .type(JsonFieldType.ARRAY)
                                .description("확정된 참여 ID 목록"),
                            fieldWithPath("buncheolConfirmed")
                                .type(JsonFieldType.BOOLEAN)
                                .description("이 확인으로 분철이 진행 확정됐는가"))
                        .requestSchema(Schema.schema("BundleConfirmRequest"))
                        .responseSchema(Schema.schema("BundleConfirmResponse"))
                        .build())));
  }

  @Test
  void 개최자_추가_신청_수락() throws Exception {
    mockMvc
        .perform(
            post("/v1/participation-bundles/{bundleId}/accept", BUNDLE_ID)
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedSlotIds\":[232,233]}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participation-bundles-accept",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("개최자 추가 신청 수락")
                        .description(
                            """
                            진행 확정 뒤 추가 모집으로 들어온 수락 전 묶음(신청 `APPLIED`)을 **한 번에** 수락한다.
                            슬롯은 `AWAITING_PAYMENT` 가 되고 묶음 입금 기한은 **지금 + 24시간**이다. 참여자에게 계좌·금액·기한
                            알림톡이 묶음 1통으로 간다. 묶음 일부만 수락하는 것은 없다.

                            `expectedSlotIds` 에는 관리 응답에서 `acceptTarget` 이 `true` 인 그 묶음의 슬롯을 싣는다. 서버가
                            본 수락 전 슬롯 집합과 다르면 409(BCH-115)로 막는다 — 그 사이 참여자가 일부를 취소했으면 새로 고쳐 다시 본다.

                            수락 전에는 계좌가 공개되지 않아 돈이 오가지 않는다. 수락하지 않고 오래 두어도 자동 처리는 없다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `expectedSlotIds` 누락·빈 목록 |
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | 운영진 분철 |
                            | 409 | `BCH-132` (`BUNDLE_ACCEPT_NOT_ALLOWED`) | 진행 확정 분철이 아니거나 수락 전 슬롯이 없음(이미 처리됐거나 취소됨) |
                            | 409 | `BCH-115` (`BUNDLE_SLOTS_CHANGED`) | 화면이 본 슬롯 집합과 다름 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .requestSchema(Schema.schema("BundleSlotsRequest"))
                        .requestFields(
                            fieldWithPath("expectedSlotIds")
                                .type(JsonFieldType.ARRAY)
                                .description("화면이 본 수락 대상 참여 ID 목록 (필수, acceptTarget 이 true 인 슬롯)"))
                        .build())));
    then(participationBundleService).should().accept(USER_ID, BUNDLE_ID, List.of(232L, 233L));
  }

  @Test
  void 개최자_추가_신청_거절() throws Exception {
    mockMvc
        .perform(
            post("/v1/participation-bundles/{bundleId}/reject", BUNDLE_ID)
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedSlotIds\":[232]}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "participation-bundles-reject",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("개최자 추가 신청 거절")
                        .description(
                            """
                            진행 확정 뒤 추가 모집으로 들어온 수락 전 묶음(신청 `APPLIED`)을 **한 번에** 거절한다. 슬롯은
                            `CANCELLED`(취소 사유 `HOST_REJECTED`)가 되고 묶음이 닫혀 그 자리는 다시 빈다. 참여자에게 알림톡이
                            묶음 1통으로 간다. 입금 전이라 돌려줄 돈은 없다.

                            요청과 대조 규칙은 수락과 같다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `expectedSlotIds` 누락·빈 목록 |
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | 운영진 분철 |
                            | 409 | `BCH-132` (`BUNDLE_ACCEPT_NOT_ALLOWED`) | 진행 확정 분철이 아니거나 수락 전 슬롯이 없음(이미 처리됐거나 취소됨) |
                            | 409 | `BCH-115` (`BUNDLE_SLOTS_CHANGED`) | 화면이 본 슬롯 집합과 다름 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .requestSchema(Schema.schema("BundleSlotsRequest"))
                        .requestFields(
                            fieldWithPath("expectedSlotIds")
                                .type(JsonFieldType.ARRAY)
                                .description("화면이 본 거절 대상 참여 ID 목록 (필수, acceptTarget 이 true 인 슬롯)"))
                        .build())));
    then(participationBundleService).should().reject(USER_ID, BUNDLE_ID, List.of(232L));
  }

  @Test
  void 개최자_묶음_제외() throws Exception {
    given(participationBundleService.release(USER_ID, BUNDLE_ID)).willReturn(List.of(232L, 233L));

    mockMvc
        .perform(post("/v1/participation-bundles/{bundleId}/release", BUNDLE_ID).with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participation-bundles-release",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("개최자 묶음 「제외」")
                        .description(
                            """
                            진행 확정 뒤 수락한 추가 묶음이 **입금 기한(수락 + 24시간)이 지나도록 입금 확인이 안 됐으면** 그 묶음을
                            뺀다. 입금 대기·「보냈어요」 슬롯이 `CANCELLED`(취소 사유 `HOST_RELEASED`)가 되고 묶음이 닫혀 그 자리는
                            다시 빈다. 참여자에게 참여 취소 알림톡이 묶음 1통으로 간다. 「보냈어요」를 누른 묶음도 기한이 지나면 뺄 수
                            있다 — 통장에 입금이 있다면 「제외」 대신 입금 확인을 한다.

                            관리 응답의 `participants[].releasability` 가 같은 판정이다. `RELEASABLE` 일 때만 버튼을 켠다.
                            응답은 **실제로 취소된 슬롯 id** 다.

                            진행 확정 전(입금 단계)에는 「제외」가 없다 — 그때 미입금 참여는 진행 확정이 함께 뺀다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | 운영진 분철 |
                            | 409 | `BCH-133` (`BUNDLE_RELEASE_NOT_ALLOWED`) | 진행 확정 전 분철이거나 수락 전 추가 신청 |
                            | 409 | `BCH-134` (`BUNDLE_RELEASE_BEFORE_DUE`) | 입금 기한 전 |
                            | 409 | `BCH-135` (`BUNDLE_RELEASE_HAS_CONFIRMED`) | 입금 확인된 슬롯이 있음 |
                            | 409 | `BCH-067` (`PARTICIPATION_STATE_TRANSITION_INVALID`) | 이미 제외·취소된 묶음 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .responseSchema(Schema.schema("BundleReleaseResponse"))
                        .responseFields(
                            fieldWithPath("bundleId")
                                .type(JsonFieldType.NUMBER)
                                .description("「제외」한 묶음 ID"),
                            fieldWithPath("releasedParticipationIds")
                                .type(JsonFieldType.ARRAY)
                                .description("실제로 취소된 참여 ID 목록"))
                        .build())));
  }
}
