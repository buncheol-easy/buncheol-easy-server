package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.user.application.MyFavoriteGroupQueryService;
import buncheoleasy.user.application.UserFavoriteGroupService;
import buncheoleasy.user.dto.response.MyFavoriteGroupResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("UserFavoriteGroupController 문서화 테스트")
class UserFavoriteGroupControllerDocsTest extends DocsTestSupport {

  @MockitoBean private UserFavoriteGroupService userFavoriteGroupService;
  @MockitoBean private MyFavoriteGroupQueryService myFavoriteGroupQueryService;

  @Test
  void 최애_그룹_등록() throws Exception {
    // when & then
    mockMvc
        .perform(post("/v1/groups/{groupId}/favorite", 100L).with(userAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "favorite-groups-add",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("최애 그룹 등록")
                        .description(
                            """
                            사용자의 최애 그룹 목록에 K-pop 그룹을 추가한다.

                            **제한**
                            - 사용자당 **최대 5개**까지 등록 가능. 6번째 등록 시 409 반환

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `GRP-001` (`GROUP_NOT_FOUND`) | 존재하지 않는 그룹 |
                            | 409 | `GRP-003` (`FAVORITE_GROUP_ALREADY_EXISTS`) | 이미 최애로 등록된 그룹 |
                            | 409 | `GRP-005` (`FAVORITE_GROUP_LIMIT_EXCEEDED`) | 최애 그룹 등록 상한(5개) 초과 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("groupId").description("그룹 ID"))
                        .build())));
  }

  @Test
  void 최애_그룹_해제() throws Exception {
    // when & then
    mockMvc
        .perform(delete("/v1/groups/{groupId}/favorite", 100L).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "favorite-groups-remove",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("최애 그룹 해제")
                        .description(
                            """
                            사용자의 최애 그룹 목록에서 K-pop 그룹을 제거한다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `GRP-004` (`FAVORITE_GROUP_NOT_FOUND`) | 해당 그룹을 최애로 등록한 상태가 아님 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("groupId").description("그룹 ID"))
                        .build())));
  }

  @Test
  void 내_최애_그룹_목록_조회() throws Exception {
    // given
    MyFavoriteGroupResponse response =
        new MyFavoriteGroupResponse(700L, 100L, "뉴진스", "https://cdn.example.com/newjeans.jpg");
    given(myFavoriteGroupQueryService.getMyFavoriteGroups(USER_ID)).willReturn(List.of(response));

    // when & then
    mockMvc
        .perform(get("/v1/groups/favorites/me").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "favorite-groups-list-my",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("FavoriteGroup")
                        .summary("내 최애 그룹 목록 조회")
                        .description(
                            """
                            사용자가 등록한 최애 그룹 카드 리스트를 최신 등록 순으로 조회한다.

                            **응답 동작**
                            - 등록한 최애가 없으면 빈 배열 `[]` 반환
                            - 결과 개수는 **0~5개** (등록 상한 5개)
                            - `imageUrl` 은 그룹 엔티티에 등록된 대표 이미지 URL. 그룹에 이미지가 없으면 `null`
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("MyFavoriteGroupListResponse"))
                        .responseFields(
                            fieldWithPath("[].favoriteId").description("최애 등록 ID"),
                            fieldWithPath("[].groupId").description("그룹 ID"),
                            fieldWithPath("[].name").description("그룹명"),
                            fieldWithPath("[].imageUrl")
                                .description("그룹 대표 이미지 URL. 없으면 `null`")
                                .optional())
                        .build())));
  }
}
