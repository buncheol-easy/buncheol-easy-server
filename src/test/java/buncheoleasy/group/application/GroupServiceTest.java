package buncheoleasy.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.dto.response.GroupResponse;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService 단위 테스트")
class GroupServiceTest {

  @InjectMocks private GroupService groupService;

  @Mock private GroupDomainService groupDomainService;
  @Mock private BuncheolRepository buncheolRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private Clock clock;

  @Nested
  @DisplayName("그룹 검색")
  class SearchGroupsTest {

    @Test
    void 검색어는_정규화되어_전달된다() {
      given(groupDomainService.searchGroups("스트레이키즈"))
          .willReturn(List.of(new Group(1L, "스트레이 키즈", null)));

      List<GroupResponse> result = groupService.searchGroups("스트레이 키즈");

      assertThat(result).extracting(GroupResponse::name).containsExactly("스트레이 키즈");
    }

    @Test
    void 미입력이면_전체_조회로_null_을_넘긴다() {
      given(groupDomainService.searchGroups(null))
          .willReturn(List.of(new Group(1L, "아무 그룹", null)));

      assertThat(groupService.searchGroups("  ")).hasSize(1);
      verify(groupDomainService).searchGroups(null);
    }

    @Test
    void 구두점만_입력하면_전체가_아니라_빈_결과다() {
      // 정규화 결과가 비는 입력을 null 로 접어 넘기면 리포지토리의 "keyword IS NULL = 전체" 게이트가 열린다.
      assertThat(groupService.searchGroups("---")).isEmpty();
      verifyNoInteractions(groupDomainService);
    }
  }

  @Nested
  @DisplayName("멤버 이름으로 그룹 검색")
  class SearchByMemberNameTest {

    @Test
    void 구두점만_입력하면_조회하지_않고_빈_결과다() {
      assertThat(groupService.searchGroupsByMemberName("()")).isEmpty();
      verifyNoInteractions(groupDomainService);
    }
  }
}
