package buncheoleasy.aidraft.application;

import buncheoleasy.group.application.GroupService;
import buncheoleasy.group.dto.response.GroupResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 엔진이 준 그룹 이름을 우리 그룹 목록의 그룹 하나로 확정한다(first_exact). 이름 → 보조 검색어 순서로 그룹 검색을 돌려, <b>결과가 정확히 1개인
 * 첫 검색어</b>에서 확정한다. 어느 검색어도 1개로 좁혀지지 않으면 확정하지 않고 검색어만 화면에 넘긴다.
 *
 * <p>표본 30개 채점(docs/121 §5.4)에서 이 방식이 30/30, 결과를 전부 합쳐 1개일 때만 확정하는 방식은 26/30 이었다 — {@code NCT}·{@code
 * IVE} 처럼 짧은 검색어가 여러 그룹에 걸려 합집합을 깨뜨린다. 검색어 순서가 결과를 바꾸므로(예: {@code TXT} 는 0건이고 별칭 {@code TOMORROW
 * X TOGETHER} 로 확정된다) 엔진이 준 순서를 지킨다.
 */
@Component
@RequiredArgsConstructor
public class AiDraftGroupMatcher {

  /** 채점 도구와 같은 기준 — 한 글자 검색어는 너무 많은 그룹에 걸려 확정 근거가 못 된다. */
  private static final int MIN_KEYWORD_LENGTH = 2;

  private final GroupService groupService;

  public GroupMatch match(final String groupName, final List<String> keywords) {
    List<String> candidates = candidates(groupName, keywords);
    for (String candidate : candidates) {
      List<GroupResponse> found = groupService.searchGroups(candidate);
      if (found.size() == 1) {
        return new GroupMatch(found.getFirst(), candidates);
      }
    }
    return new GroupMatch(null, candidates);
  }

  /**
   * [이름] + 보조 검색어, 앞뒤 공백을 지우고 짧은 것·중복을 뺀다(순서 유지). 빈 검색어는 반드시 빼야 한다 — 그룹 검색은 빈 검색어를 "전체" 로 읽는다.
   */
  static List<String> candidates(final String groupName, final List<String> keywords) {
    Set<String> unique = new LinkedHashSet<>();
    Stream.concat(Stream.of(groupName), keywords == null ? Stream.empty() : keywords.stream())
        .filter(Objects::nonNull)
        .map(String::strip)
        .filter(keyword -> keyword.length() >= MIN_KEYWORD_LENGTH)
        .forEach(unique::add);
    return List.copyOf(unique);
  }

  /**
   * @param group 확정한 그룹. 확정하지 못했으면 null
   * @param keywords 시도한 검색어(시도 순서). 화면이 그룹 검색 칸에 미리 채운다
   */
  public record GroupMatch(GroupResponse group, List<String> keywords) {}
}
