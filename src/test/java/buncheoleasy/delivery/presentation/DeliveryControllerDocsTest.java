package buncheoleasy.delivery.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.delivery.application.DeliveryService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("DeliveryController 문서화 테스트")
class DeliveryControllerDocsTest extends DocsTestSupport {

  @MockitoBean private DeliveryService deliveryService;

  @Test
  void 운송장_등록() throws Exception {
    // when & then
    mockMvc
        .perform(
            patch("/v1/deliveries/{id}/tracking", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\": \"6079123456789\"}")
                .with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "deliveries-register-tracking",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Delivery")
                        .summary("운송장 등록")
                        .description(
                            """
                            개최자(운영자)가 확정 참여자의 배송 건에 운송장 번호를 등록한다. 등록 시 배송 상태가
                            `SNAPSHOTTED` → `SHIPPING` 으로 전이되고, 참여자에게 운송장 등록 알림(알림톡)이 발송된다.
                            분철이 진행확정(CONFIRMED)된 뒤에만 등록할 수 있다 — 모집중 발송을 허용하면 마감 시점
                            취소(최소 인원 미달)와 이미 발송된 물건이 모순되기 때문.

                            이미 `SHIPPING` 상태인 배송 건에 다시 호출하면 상태 전이 없이 운송장 번호만 갱신된다
                            (재등록 허용, 참여자에게 운송장 등록 알림이 다시 발송된다).

                            **권한**: 해당 배송이 속한 분철의 **개최자 본인만** 호출 가능.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `trackingNumber` 누락/공백 |
                            | 403 | `BCH-044` (`BUNCHEOL_NO_PERMISSION`) | 분철 개최자가 아님 |
                            | 404 | `DLV-006` (`DELIVERY_NOT_FOUND`) | 존재하지 않는 배송 정보 |
                            | 409 | `DLV-009` (`DELIVERY_BUNCHEOL_NOT_CONFIRMED`) | 분철이 아직 진행확정 전 |
                            | 409 | `DLV-007` (`DELIVERY_STATE_TRANSITION_INVALID`) | 현재 배송 상태에서 운송장을 등록할 수 없음 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("배송 ID"))
                        .requestSchema(Schema.schema("TrackingRegistrationRequest"))
                        .requestFields(
                            fieldWithPath("trackingNumber").description("운송장 번호 (공백 불가)"))
                        .build())));
  }

  @Test
  void 수령_확인() throws Exception {
    // when & then
    mockMvc
        .perform(post("/v1/deliveries/{id}/receipt", 10L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "deliveries-confirm-receipt",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Delivery")
                        .summary("수령 확인")
                        .description(
                            """
                            참여자가 상품 수령을 확인한다. 배송 상태가 `RECEIVED` 로 전이된다.
                            수령 확인은 배송 상태가 `SHIPPING`(배송중) 또는 `DELIVERED`(배송완료)일 때만 가능하다 —
                            그 외 상태(운송장 등록 전 `SNAPSHOTTED`, 이미 수령 확인된 `RECEIVED`)에서는
                            `DLV-007` 이 발생한다.

                            **권한**: 해당 배송의 **참여자 본인만** 호출 가능.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `DLV-008` (`DELIVERY_NO_PERMISSION`) | 배송의 참여자 본인이 아님 |
                            | 404 | `DLV-006` (`DELIVERY_NOT_FOUND`) | 존재하지 않는 배송 정보 |
                            | 409 | `DLV-007` (`DELIVERY_STATE_TRANSITION_INVALID`) | 현재 배송 상태에서 수령 확인을 할 수 없음 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("배송 ID"))
                        .build())));
  }
}
