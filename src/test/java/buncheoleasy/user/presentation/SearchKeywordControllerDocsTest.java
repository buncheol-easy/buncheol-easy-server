package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.user.application.recentsearch.UserRecentSearchQueryService;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@DisplayName("SearchKeywordController 문서화 테스트")
class SearchKeywordControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private UserRecentSearchQueryService userRecentSearchQueryService;

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
  void 최근_검색어_조회() throws Exception {
    given(userRecentSearchQueryService.getRecent(ArgumentMatchers.any()))
        .willReturn(
            List.of(
                new RecentSearchResponse(20L, "뉴진스"),
                new RecentSearchResponse(11L, "민지")));

    mockMvc
        .perform(
            get("/v1/search-keywords/recent")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
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
                        .requestHeaders(
                            headerWithName("Authorization")
                                .description("`Bearer {accessToken}` — 비로그인 호출이면 헤더 자체를 생략")
                                .optional())
                        .responseSchema(Schema.schema("RecentSearchListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("최근 검색 이력 ID"),
                            fieldWithPath("[].keyword").description("사용자가 검색창에 친 텍스트"))
                        .build())));
  }
}
