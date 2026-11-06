package buncheoleasy.aidraft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import buncheoleasy.aidraft.application.AiDraftGroupMatcher.GroupMatch;
import buncheoleasy.group.application.GroupService;
import buncheoleasy.group.dto.response.GroupResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiDraftGroupMatcher 단위 테스트")
class AiDraftGroupMatcherTest {

  private static final GroupResponse TXT =
      new GroupResponse(12L, "투모로우바이투게더", null, List.of("TOMORROW X TOGETHER"));
  private static final GroupResponse NCT_127 = new GroupResponse(20L, "NCT 127", null, List.of());
  private static final GroupResponse NCT_DREAM = new GroupResponse(21L, "NCT DREAM", null, List.of());
  private static final GroupResponse NCT_WISH = new GroupResponse(22L, "NCT WISH", null, List.of());

  @Mock private GroupService groupService;

  private AiDraftGroupMatcher matcher;

  @BeforeEach
  void setUp() {
    matcher = new AiDraftGroupMatcher(groupService);
  }

  @Nested
  @DisplayName("first_exact")
  class FirstExactTest {

    @Test
    void 이름으로_정확히_하나가_나오면_거기서_확정하고_더_찾지_않는다() {
      given(groupService.searchGroups("투모로우바이투게더")).willReturn(List.of(TXT));

      GroupMatch match = matcher.match("투모로우바이투게더", List.of("TXT"));

      assertThat(match.group()).isEqualTo(TXT);
      then(groupService).should(never()).searchGroups("TXT");
    }

    @Test
    void 이름이_0건이면_다음_검색어로_확정한다_TXT_사례() {
      // staging·prod 모두 'TXT' 는 별칭에 없어 0건 — 정식 이름(별칭)이 먼저 와야 확정된다.
      given(groupService.searchGroups("투모로우바이투게더 (TXT)")).willReturn(List.of());
      given(groupService.searchGroups("TOMORROW X TOGETHER")).willReturn(List.of(TXT));

      GroupMatch match =
          matcher.match("투모로우바이투게더 (TXT)", List.of("TOMORROW X TOGETHER", "TXT"));

      assertThat(match.group()).isEqualTo(TXT);
      then(groupService).should(never()).searchGroups("TXT");
    }

    @Test
    void 첫_검색어가_여러_건이면_넘어가고_정확히_하나인_다음_검색어에서_확정한다() {
      given(groupService.searchGroups("NCT")).willReturn(List.of(NCT_127, NCT_DREAM, NCT_WISH));
      given(groupService.searchGroups("NCT DREAM")).willReturn(List.of(NCT_DREAM));

      GroupMatch match = matcher.match("NCT", List.of("NCT DREAM", "엔시티 드림"));

      assertThat(match.group()).isEqualTo(NCT_DREAM);
    }

    @Test
    void 검색어_순서를_지킨다() {
      // 순서가 결과를 바꾼다 — 뒤 검색어가 다른 그룹 하나로 좁혀져도 앞에서 확정된 것이 이긴다.
      given(groupService.searchGroups("NCT 127")).willReturn(List.of(NCT_127));

      GroupMatch match = matcher.match("NCT 127", List.of("NCT DREAM"));

      assertThat(match.group()).isEqualTo(NCT_127);
      then(groupService).should(never()).searchGroups("NCT DREAM");
    }

    @Test
    void 어느_검색어도_하나로_좁혀지지_않으면_확정하지_않고_검색어만_준다() {
      given(groupService.searchGroups(anyString())).willReturn(List.of());
      given(groupService.searchGroups("NCT")).willReturn(List.of(NCT_127, NCT_DREAM));

      GroupMatch match = matcher.match("지코", List.of("NCT", "ZICO"));

      assertThat(match.group()).isNull();
      assertThat(match.keywords()).containsExactly("지코", "NCT", "ZICO");
      InOrder order = inOrder(groupService);
      order.verify(groupService).searchGroups("지코");
      order.verify(groupService).searchGroups("NCT");
      order.verify(groupService).searchGroups("ZICO");
    }
  }

  @Nested
  @DisplayName("검색어 만들기")
  class CandidatesTest {

    @Test
    void 이름을_앞에_두고_공백을_지우고_중복과_한_글자를_뺀다() {
      List<String> candidates =
          AiDraftGroupMatcher.candidates(
              " 아이브 ", Arrays.asList("IVE", "아이브", null, "  ", "V", "IVE "));

      assertThat(candidates).containsExactly("아이브", "IVE");
    }

    @Test
    void 이름이_없어도_보조_검색어로_찾는다() {
      assertThat(AiDraftGroupMatcher.candidates(null, List.of("TXT"))).containsExactly("TXT");
      assertThat(AiDraftGroupMatcher.candidates(null, null)).isEmpty();
    }

    @Test
    void 빈_검색어로는_검색하지_않는다() {
      // 그룹 검색은 빈 검색어를 "전체" 로 읽는다 — 그룹이 하나뿐인 환경이면 아무 그룹이나 확정된다.
      GroupMatch match = matcher.match("  ", List.of("", " "));

      assertThat(match.group()).isNull();
      then(groupService).shouldHaveNoInteractions();
    }
  }
}
