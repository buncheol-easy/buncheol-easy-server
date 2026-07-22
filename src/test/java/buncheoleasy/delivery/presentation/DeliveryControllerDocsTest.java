package buncheoleasy.delivery.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.delivery.application.DeliveryService;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.Collections;
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
@DisplayName("DeliveryController 문서화 테스트")
class DeliveryControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private DeliveryService deliveryService;
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
              new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Test
  void 운송장_등록() throws Exception {
    mockMvc
        .perform(
            patch("/v1/deliveries/{id}/tracking", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\": \"6079123456789\"}")
                .with(mockAuth()))
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
                        .pathParameters(parameterWithName("id").description("배송 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestFields(
                            fieldWithPath("trackingNumber").description("운송장 번호 (공백 불가)"))
                        .requestSchema(Schema.schema("TrackingRegistrationRequest"))
                        .build())));
  }

  @Test
  void 수령_확인() throws Exception {
    mockMvc
        .perform(
            post("/v1/deliveries/{id}/receipt", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
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

                            **권한**: 해당 배송의 **참여자 본인만** 호출 가능.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 403 | `DLV-008` (`DELIVERY_NO_PERMISSION`) | 배송의 참여자 본인이 아님 |
                            | 404 | `DLV-006` (`DELIVERY_NOT_FOUND`) | 존재하지 않는 배송 정보 |
                            | 409 | `DLV-007` (`DELIVERY_STATE_TRANSITION_INVALID`) | 현재 배송 상태에서 수령 확인을 할 수 없음 |
                            """)
                        .pathParameters(parameterWithName("id").description("배송 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
