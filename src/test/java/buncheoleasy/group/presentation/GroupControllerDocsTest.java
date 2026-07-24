package buncheoleasy.group.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.group.application.GroupService;
import buncheoleasy.group.dto.response.GroupMemberResponse;
import buncheoleasy.group.dto.response.GroupResponse;
import buncheoleasy.group.dto.response.GroupWithMembersResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("GroupController 문서화 테스트")
class GroupControllerDocsTest extends DocsTestSupport {

  @MockitoBean private GroupService groupService;

  @Test
  void 그룹_검색() throws Exception {
    // given
    given(groupService.searchGroups("뉴진"))
        .willReturn(
            List.of(
                new GroupResponse(1L, "NewJeans", "https://example.com/newjeans.jpg"),
                new GroupResponse(2L, "뉴진스", "https://example.com/newjeans.jpg")));

    // when & then
    mockMvc
        .perform(get("/v1/groups").param("keyword", "뉴진"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "groups-search",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Group")
                        .summary("그룹 검색")
                        .description("keyword 로 그룹 이름을 부분 검색한다. 미입력 시 전체 그룹 반환.")
                        .queryParameters(
                            parameterWithName("keyword")
                                .description("그룹 이름 검색 키워드 (선택)")
                                .optional())
                        .responseSchema(Schema.schema("GroupListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("그룹 ID"),
                            fieldWithPath("[].name").description("그룹 이름"),
                            fieldWithPath("[].image").description("그룹 이미지 URL").optional())
                        .build())));
  }

  @Test
  void 멤버_이름으로_그룹_검색() throws Exception {
    // given
    given(groupService.searchGroupsByMemberName("민지"))
        .willReturn(
            List.of(
                new GroupWithMembersResponse(
                    1L,
                    "NewJeans",
                    "https://example.com/newjeans.jpg",
                    List.of(
                        new GroupMemberResponse(10L, "민지", "https://example.com/minji.jpg"),
                        new GroupMemberResponse(11L, "하니", "https://example.com/hani.jpg")))));

    // when & then
    mockMvc
        .perform(get("/v1/groups/members").param("keyword", "민지"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "groups-search-by-member-name",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Group")
                        .summary("멤버 이름으로 그룹 검색")
                        .description("멤버 이름과 keyword 가 정확히 일치하는 멤버가 속한 그룹과 그 그룹의 전 멤버를 함께 반환한다.")
                        .queryParameters(
                            parameterWithName("keyword").description("정확히 일치시킬 멤버 이름 (최대 100자)"))
                        .responseSchema(Schema.schema("GroupWithMembersListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("그룹 ID"),
                            fieldWithPath("[].name").description("그룹 이름"),
                            fieldWithPath("[].image").description("그룹 이미지 URL").optional(),
                            fieldWithPath("[].members[].id").description("멤버 ID"),
                            fieldWithPath("[].members[].name").description("멤버 이름"),
                            fieldWithPath("[].members[].image")
                                .description("멤버 이미지 URL")
                                .optional())
                        .build())));
  }

  @Test
  void 인기_아티스트_조회() throws Exception {
    // given
    given(groupService.getPopularGroups())
        .willReturn(
            List.of(
                new GroupResponse(1L, "NewJeans", "https://example.com/newjeans.jpg"),
                new GroupResponse(2L, "aespa", "https://example.com/aespa.jpg"),
                new GroupResponse(3L, "IVE", "https://example.com/ive.jpg")));

    // when & then
    mockMvc
        .perform(get("/v1/groups/popular"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "groups-popular",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Group")
                        .summary("인기 아티스트 조회")
                        .description(
                            "최근 30일간 분철 등록 수가 많은 순으로 그룹 상위 5개를 반환한다. "
                                + "모집중(RECRUITING)·진행확정(CONFIRMED) 분철만 집계한다 (취소된 분철 제외).")
                        .responseSchema(Schema.schema("GroupListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("그룹 ID"),
                            fieldWithPath("[].name").description("그룹 이름"),
                            fieldWithPath("[].image").description("그룹 이미지 URL").optional())
                        .build())));
  }

  @Test
  void 그룹_멤버_조회() throws Exception {
    // given
    given(groupService.getGroupMembers(1L))
        .willReturn(
            List.of(
                new GroupMemberResponse(10L, "민지", "https://example.com/minji.jpg"),
                new GroupMemberResponse(11L, "하니", "https://example.com/hani.jpg")));

    // when & then
    mockMvc
        .perform(get("/v1/groups/{groupId}/members", 1L))
        .andExpect(status().isOk())
        .andDo(
            document(
                "groups-members",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Group")
                        .summary("그룹 멤버 조회")
                        .description(
                            "그룹에 속한 멤버 목록을 조회한다. "
                                + "존재하지 않는 그룹이면 404 `GRP-001` (`GROUP_NOT_FOUND`) 이 발생한다.")
                        .pathParameters(parameterWithName("groupId").description("그룹 ID"))
                        .responseSchema(Schema.schema("GroupMemberListResponse"))
                        .responseFields(
                            fieldWithPath("[].id").description("멤버 ID"),
                            fieldWithPath("[].name").description("멤버 이름"),
                            fieldWithPath("[].image").description("멤버 이미지 URL").optional())
                        .build())));
  }
}
