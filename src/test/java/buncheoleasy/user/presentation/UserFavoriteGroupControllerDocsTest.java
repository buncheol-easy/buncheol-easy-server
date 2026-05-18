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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.user.application.MyFavoriteGroupQueryService;
import buncheoleasy.user.application.UserFavoriteGroupService;
import buncheoleasy.user.dto.response.MyFavoriteGroupResponse;
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
@DisplayName("UserFavoriteGroupController 문서화 테스트")
class UserFavoriteGroupControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private UserFavoriteGroupService userFavoriteGroupService;
  @MockitoBean private MyFavoriteGroupQueryService myFavoriteGroupQueryService;
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
  void 최애_그룹_등록() throws Exception {
    mockMvc
        .perform(
            post("/v1/groups/{groupId}/favorite", 100L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "favorite-groups-add",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("최애 그룹 등록")
                        .description("사용자의 최애 그룹 목록에 K-pop 그룹을 추가한다. 이미 등록된 그룹이면 409 반환.")
                        .pathParameters(parameterWithName("groupId").description("그룹 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 최애_그룹_해제() throws Exception {
    mockMvc
        .perform(
            delete("/v1/groups/{groupId}/favorite", 100L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "favorite-groups-remove",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("최애 그룹 해제")
                        .description("사용자의 최애 그룹 목록에서 K-pop 그룹을 제거한다. 등록되지 않은 그룹이면 404 반환.")
                        .pathParameters(parameterWithName("groupId").description("그룹 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }

  @Test
  void 내_최애_그룹_목록_조회() throws Exception {
    MyFavoriteGroupResponse response =
        new MyFavoriteGroupResponse(700L, 100L, "뉴진스", "https://cdn.example.com/newjeans.jpg");
    given(myFavoriteGroupQueryService.getMyFavoriteGroups(USER_ID)).willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/groups/favorites/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "favorite-groups-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("내 최애 그룹 목록 조회")
                        .description("마이페이지에서 사용자가 등록한 최애 그룹 카드 리스트를 최신 등록 순으로 조회한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("MyFavoriteGroupListResponse"))
                        .responseFields(
                            fieldWithPath("[].favoriteId").description("최애 등록 ID"),
                            fieldWithPath("[].groupId").description("그룹 ID"),
                            fieldWithPath("[].name").description("그룹명"),
                            fieldWithPath("[].imageUrl")
                                .description("그룹 대표 이미지 URL. 없으면 null")
                                .optional())
                        .build())));
  }
}
