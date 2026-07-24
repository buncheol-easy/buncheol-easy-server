package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.user.application.recentsearch.UserRecentSearchQueryService;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("SearchKeywordController 문서화 테스트")
class SearchKeywordControllerDocsTest extends DocsTestSupport {

  @MockitoBean private UserRecentSearchQueryService userRecentSearchQueryService;

  @Test
  void 최근_검색어_조회() throws Exception {
    // given
    given(userRecentSearchQueryService.getRecent(USER_ID))
        .willReturn(
            List.of(
                new RecentSearchResponse(20L, "뉴진스"),
                new RecentSearchResponse(11L, "민지")));

    // when & then
    mockMvc
        .perform(get("/v1/search-keywords/recent").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "search-keywords-recent",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("SearchKeyword")
                        .summary("최근 검색어 조회")
                        .description(
                            "로그인 사용자가 검색창에 친 텍스트를 최신순으로 최대 7개 반환한다. 비로그인 시 빈 배열. "
                                + "프론트가 그룹·멤버 name → id 변환을 책임지므로 응답은 단일 텍스트 컬럼만 노출한다.")
                        .requestHeaders(optionalUserAuthorizationHeader())
                        .responseSchema(Schema.schema("RecentSearchListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("최근 검색 이력 ID"),
                            fieldWithPath("[].keyword").description("사용자가 검색창에 친 텍스트"))
                        .build())));
  }
}
