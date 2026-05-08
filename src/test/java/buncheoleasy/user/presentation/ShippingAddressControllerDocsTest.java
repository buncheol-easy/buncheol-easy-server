package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.user.application.ShippingAddressService;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
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
@DisplayName("ShippingAddressController 문서화 테스트")
class ShippingAddressControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private ShippingAddressService shippingAddressService;

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
  void 배송지_등록() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/v1/users/me/shipping-addresses")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"GS25 강남점\",\"alias\":\"회사\",\"isDefault\":true}"))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "shipping-addresses-register",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 등록")
                        .description("최대 5개까지 등록 가능. isDefault=true 시 기존 기본 배송지는 해제된다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("ShippingAddressRequest"))
                        .requestFields(
                            fieldWithPath("shippingMethod")
                                .description("배송 방식 (GS25_HALF / CU_HALF)"),
                            fieldWithPath("storeName").description("수령 매장 이름 (1~100자)"),
                            fieldWithPath("alias").description("배송지 별칭 (10자 이하)").optional(),
                            fieldWithPath("isDefault")
                                .description("기본 배송지 여부 (기본 false)")
                                .optional())
                        .build())));
  }

  @Test
  void 배송지_조회() throws Exception {
    // given
    given(shippingAddressService.getUserShippingAddresses(USER_ID))
        .willReturn(
            List.of(
                ShippingAddressResponse.of(1L, "GS25_HALF", "GS25 강남점", "회사", true),
                ShippingAddressResponse.of(2L, "CU_HALF", "CU 광화문점", null, false)));

    // when & then
    mockMvc
        .perform(
            get("/v1/users/me/shipping-addresses")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "shipping-addresses-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 목록 조회")
                        .description("로그인 사용자가 등록한 배송지 목록을 반환한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("ShippingAddressListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("배송지 ID"),
                            fieldWithPath("[].shippingMethod").description("배송 방식"),
                            fieldWithPath("[].storeName").description("수령 매장 이름"),
                            fieldWithPath("[].alias").description("배송지 별칭").optional(),
                            fieldWithPath("[].isDefault").description("기본 배송지 여부"))
                        .build())));
  }

  @Test
  void 배송지_수정() throws Exception {
    // when & then
    mockMvc
        .perform(
            put("/v1/users/me/shipping-addresses/{id}", 1L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"GS25 광화문점\",\"alias\":\"회사\",\"isDefault\":false}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "shipping-addresses-modify",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 수정")
                        .pathParameters(parameterWithName("id").description("배송지 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("ShippingAddressRequest"))
                        .requestFields(
                            fieldWithPath("shippingMethod")
                                .description("배송 방식 (GS25_HALF / CU_HALF)"),
                            fieldWithPath("storeName").description("수령 매장 이름 (1~100자)"),
                            fieldWithPath("alias").description("배송지 별칭 (10자 이하)").optional(),
                            fieldWithPath("isDefault")
                                .description("기본 배송지 여부 (기본 false)")
                                .optional())
                        .build())));
  }

  @Test
  void 배송지_삭제() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/v1/users/me/shipping-addresses/{id}", 1L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "shipping-addresses-remove",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 삭제")
                        .pathParameters(parameterWithName("id").description("배송지 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
