package buncheoleasy.cvsstore.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.cvsstore.application.CvsStoreService;
import buncheoleasy.cvsstore.dto.response.CvsStoreResponse;
import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.global.page.CursorResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("CvsStoreController 문서화 테스트")
class CvsStoreControllerDocsTest extends DocsTestSupport {

  @MockitoBean private CvsStoreService cvsStoreService;

  @Test
  void 편의점_접수처_검색() throws Exception {
    // given
    given(cvsStoreService.searchStores("GS25", "강남", null, 20))
        .willReturn(
            new CursorResponse<>(
                List.of(
                    new CvsStoreResponse(
                        101L,
                        "GS25",
                        "V0001",
                        "GS25강남점",
                        "021234567",
                        "서울 강남구 테헤란로 1",
                        "06236",
                        new BigDecimal("37.5010000"),
                        new BigDecimal("127.0390000")),
                    new CvsStoreResponse(
                        108L,
                        "GS25",
                        "V0008",
                        "GS25강남타워점",
                        "0809995425",
                        "서울 강남구 역삼로 2",
                        "06241",
                        new BigDecimal("37.4990000"),
                        new BigDecimal("127.0310000"))),
                "0_108",
                true));

    // when & then
    mockMvc
        .perform(get("/v1/cvs-stores").param("brand", "GS25").param("keyword", "강남"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "cvs-stores-search",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("CvsStore")
                        .summary("편의점 접수처 검색")
                        .description(
                            """
                            배송지 등록용 편의점 택배 접수처를 검색한다. keyword 는 지점명·주소 부분일치(대소문자 무관), \
                            brand 는 GS25/CU 필터(미입력 시 전체)다. 픽업(수령) 가능 점포만 반환한다. 비로그인 호출 가능.

                            정렬: 지점명이 keyword 와 일치하는 매장을 먼저, 주소만 일치하는 매장을 뒤에 노출하며 \
                            각 그룹 안에서는 id 오름차순이다. 커서는 직전 응답의 `nextCursor` 를 그대로 전달하는 불투명 토큰.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `keyword` 가 100자 초과 |
                            | 400 | `CVS-001` (`CVS_BRAND_INVALID`) | `brand` 가 GS25/CU 가 아님 |
                            | 400 | `PAGE-001` (`CURSOR_INVALID`) | 커서 형식 오류 (그룹/id 파싱 실패) |
                            """)
                        .queryParameters(
                            parameterWithName("brand")
                                .description("편의점 브랜드 필터: GS25 | CU (선택, 미입력 시 전체)")
                                .optional(),
                            parameterWithName("keyword")
                                .description("지점명·주소 검색 키워드 (선택, 최대 100자)")
                                .optional(),
                            parameterWithName("cursor")
                                .description("이전 응답의 nextCursor (선택, 첫 페이지는 미입력)")
                                .optional(),
                            parameterWithName("size")
                                .description("페이지 크기 (선택, 기본 20, 1~50 으로 보정)")
                                .optional())
                        .responseSchema(Schema.schema("CvsStoreListResponse"))
                        .responseFields(
                            fieldWithPath("items").description("접수처 배열"),
                            fieldWithPath("items[].id").description("접수처 ID"),
                            fieldWithPath("items[].brand").description("편의점 브랜드 (GS25 | CU)"),
                            fieldWithPath("items[].storeCode")
                                .description("원천(브랜드) 점포 코드 — 배송지 등록 시 storeCode 로 전달"),
                            fieldWithPath("items[].name").description("지점명"),
                            fieldWithPath("items[].tel").description("지점 전화번호").optional(),
                            fieldWithPath("items[].address").description("도로명 주소"),
                            fieldWithPath("items[].postNo").description("우편번호").optional(),
                            fieldWithPath("items[].latitude").description("WGS84 위도 (지도 마커용)"),
                            fieldWithPath("items[].longitude").description("WGS84 경도 (지도 마커용)"),
                            fieldWithPath("nextCursor")
                                .description("다음 페이지 커서 (마지막 페이지면 null)")
                                .optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"))
                        .build())));
  }
}
