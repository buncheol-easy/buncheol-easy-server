package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.user.application.ShippingAddressService;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("ShippingAddressController 문서화 테스트")
class ShippingAddressControllerDocsTest extends DocsTestSupport {

  @MockitoBean private ShippingAddressService shippingAddressService;

  @Test
  void 배송지_등록() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/v1/users/me/shipping-addresses")
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"GS25 강남점\",\"storeCode\":\"VKK99\",\"alias\":\"회사\",\"isDefault\":true}"))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "shipping-addresses-register",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 등록")
                        .description(
                            "최대 5개까지 등록 가능하며 6개째는 400 `USR-020` (`SHIPPING_ADDRESS_LIMIT_EXCEEDED`) 로 거부된다. "
                                + "isDefault=true 시 같은 배송 방식(shippingMethod) 내의 기존 기본 배송지가 해제된다. "
                                + "프로필 미완료 유저는 403 `USR-018` (`USER_PROFILE_IS_NOT_COMPLETE`), "
                                + "같은 배송 방식+매장 조합이 이미 등록돼 있으면 409 `USR-021` (`SHIPPING_ADDRESS_DUPLICATE`) 로 거부된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .requestSchema(Schema.schema("ShippingAddressRequest"))
                        .requestFields(
                            fieldWithPath("shippingMethod")
                                .description("배송 방식 (GS25_HALF / CU_HALF)"),
                            fieldWithPath("storeName").description("수령 매장 이름 (1~100자)"),
                            fieldWithPath("storeCode")
                                .description(
                                    "접수처 검색(GET /v1/cvs-stores)에서 받은 원천 점포 코드 (20자 이하, 선택). "
                                        + "수정 시 미전달하면 지점명이 그대로일 때만 기존 코드가 유지된다")
                                .optional(),
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
                ShippingAddressResponse.of(1L, "GS25_HALF", "GS25 강남점", "VKK99", "회사", true),
                ShippingAddressResponse.of(2L, "CU_HALF", "CU 광화문점", "16031", null, false)));

    // when & then
    mockMvc
        .perform(get("/v1/users/me/shipping-addresses").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "shipping-addresses-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 목록 조회")
                        .description("로그인 사용자가 등록한 배송지 목록을 반환한다.")
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("ShippingAddressListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("배송지 ID"),
                            fieldWithPath("[].shippingMethod").description("배송 방식"),
                            fieldWithPath("[].storeName").description("수령 매장 이름"),
                            fieldWithPath("[].storeCode")
                                .description("원천 점포 코드 (자유입력 등록분은 null)")
                                .optional(),
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
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"shippingMethod\":\"GS25_HALF\",\"storeName\":\"GS25 광화문점\",\"storeCode\":\"V0021\",\"alias\":\"회사\",\"isDefault\":false}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "shipping-addresses-modify",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 수정")
                        .description(
                            "배송지 정보를 수정한다. isDefault=true 시 같은 배송 방식(shippingMethod) 내의 기존 기본 배송지가 해제된다. "
                                + "본인 배송지가 아니면 403 `USR-022` (`SHIPPING_ADDRESS_FORBIDDEN`), "
                                + "같은 배송 방식+매장 조합이 이미 등록돼 있으면 409 `USR-021` (`SHIPPING_ADDRESS_DUPLICATE`) 로 거부된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("배송지 ID"))
                        .requestSchema(Schema.schema("ShippingAddressRequest"))
                        .requestFields(
                            fieldWithPath("shippingMethod")
                                .description("배송 방식 (GS25_HALF / CU_HALF)"),
                            fieldWithPath("storeName").description("수령 매장 이름 (1~100자)"),
                            fieldWithPath("storeCode")
                                .description(
                                    "접수처 검색(GET /v1/cvs-stores)에서 받은 원천 점포 코드 (20자 이하, 선택). "
                                        + "수정 시 미전달하면 지점명이 그대로일 때만 기존 코드가 유지된다")
                                .optional(),
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
        .perform(delete("/v1/users/me/shipping-addresses/{id}", 1L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "shipping-addresses-remove",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ShippingAddress")
                        .summary("배송지 삭제")
                        .description(
                            "배송지를 삭제한다. 본인 배송지가 아니면 403 `USR-022` (`SHIPPING_ADDRESS_FORBIDDEN`), "
                                + "활성(입금대기중·입금확인됨) 참여가 참조 중인 배송지면 409 `USR-030` "
                                + "(`SHIPPING_ADDRESS_DELETE_BLOCKED_BY_ACTIVE_PARTICIPATION`) 로 거부된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("배송지 ID"))
                        .build())));
  }
}
