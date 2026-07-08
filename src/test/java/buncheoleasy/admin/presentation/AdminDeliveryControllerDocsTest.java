package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminDeliveryCommandService;
import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AdminDeliveryController 문서화 테스트")
class AdminDeliveryControllerDocsTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private AdminDeliveryCommandService adminDeliveryCommandService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void 관리자_운송장_등록_벌크_처리() throws Exception {
    // given
    given(adminDeliveryCommandService.registerTracking(anyList(), anyString()))
        .willReturn(
            new AdminBulkResultResponse(
                List.of(7L, 8L),
                List.of(
                    new AdminBulkResultResponse.Failure(
                        9L, "DLV-007", "현재 배송 상태에서는 해당 작업을 수행할 수 없습니다."))));

    // when & then
    mockMvc
        .perform(
            patch("/v1/admin/deliveries/tracking")
                .header("Authorization", "Bearer {accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryIds\": [7, 8, 9], \"trackingNumber\": \"TRACK-1234\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-deliveries-tracking",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 운송장 등록 벌크 처리")
                        .description(
                            """
                            같은 묶음배송의 여러 배송 건에 동일한 운송장 번호를 한 번에 등록한다 (ROLE_ADMIN 전용).
                            개최자 소유권 검증 없이 전체 배송에 적용되며, 건별 독립 처리라 일부 실패가 나머지 성공을 되돌리지 않는다.""")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("AdminTrackingRegistrationRequest"))
                        .requestFields(
                            fieldWithPath("deliveryIds").description("운송장을 등록할 배송 ID 목록"),
                            fieldWithPath("trackingNumber").description("운송장 번호"))
                        .responseSchema(Schema.schema("AdminBulkResultResponse"))
                        .responseFields(
                            fieldWithPath("succeededIds").description("등록에 성공한 배송 ID 목록"),
                            fieldWithPath("failures[].id").description("실패한 배송 ID"),
                            fieldWithPath("failures[].code").description("실패 사유 에러 코드"),
                            fieldWithPath("failures[].message").description("실패 사유 메시지"))
                        .build())));
  }
}
