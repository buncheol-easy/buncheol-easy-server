package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 분철 목록(둘러보기/검색) 조회.
 *
 * <p>비로그인 호출도 허용한다 ({@code userId == null} 이면 모든 항목의 {@code bookmarked} 가 false). 정렬은 {@code
 * createdAt DESC, id DESC} 고정, hasNext 판별을 위해 size+1 fetch 패턴을 사용한다.
 */
@Service
@RequiredArgsConstructor
public class BuncheolListQueryService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 50;

  private final BuncheolRepository buncheolRepository;
  private final GroupRepository groupRepository;
  private final BuncheolBookmarkRepository buncheolBookmarkRepository;
  private final BuncheolMemberNameResolver buncheolMemberNameResolver;

  @Transactional(readOnly = true)
  public CursorResponse<BuncheolSummaryResponse> search(
      final Long userId,
      final BuncheolSearchCondition condition,
      final Cursor cursor,
      final int requestedSize) {
    final int safeSize = clampSize(requestedSize);
    final BuncheolSearchCondition normalized = normalizeKeyword(condition);

    final List<Buncheol> fetched = buncheolRepository.search(normalized, cursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<Buncheol> visible = hasNext ? fetched.subList(0, safeSize) : fetched;
    if (visible.isEmpty()) {
      return CursorResponse.empty();
    }

    final List<Long> buncheolIds = visible.stream().map(Buncheol::getId).toList();
    final List<Long> groupIds = visible.stream().map(Buncheol::getGroupId).distinct().toList();

    final Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));
    final Map<Long, List<String>> memberNamesByBuncheolId =
        buncheolMemberNameResolver.findNamesByBuncheolIds(buncheolIds);
    final Set<Long> bookmarkedBuncheolIds =
        userId == null
            ? Set.of()
            : buncheolBookmarkRepository.findBookmarkedBuncheolIds(userId, buncheolIds);

    final List<BuncheolSummaryResponse> items =
        visible.stream()
            .map(
                b ->
                    new BuncheolSummaryResponse(
                        b.getId(),
                        b.getTitle(),
                        b.getDeadline(),
                        bookmarkedBuncheolIds.contains(b.getId()),
                        groupNameById.get(b.getGroupId()),
                        memberNamesByBuncheolId.getOrDefault(b.getId(), List.of())))
            .toList();

    final String nextCursor = hasNext ? Cursor.from(visible.getLast()).encode() : null;
    return new CursorResponse<>(items, nextCursor, hasNext);
  }

  private int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }

  private BuncheolSearchCondition normalizeKeyword(final BuncheolSearchCondition condition) {
    final String keyword = condition.keyword();
    if (keyword == null || keyword.isBlank()) {
      return new BuncheolSearchCondition(condition.groupId(), condition.memberId(), null);
    }
    final String escaped = escapeForLike(keyword.trim());
    return new BuncheolSearchCondition(condition.groupId(), condition.memberId(), escaped);
  }

  // LIKE 와일드카드(`%`, `_`) 와 이스케이프 문자 자체(`\`)를 리터럴로 매칭하도록 이스케이프한다.
  // 호출 측 JPQL 의 ESCAPE 절은 Java 리터럴 `"ESCAPE '\\'"` 로 작성한다
  // (실제 SQL 로 전달되는 이스케이프 문자는 단일 역슬래시 `\`).
  private static String escapeForLike(final String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
