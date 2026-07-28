package buncheoleasy.buncheol.application.bookmark;

import buncheoleasy.buncheol.application.BuncheolMemberNameResolver;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.dto.request.BookmarkSortOption;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyBookmarkedBuncheolQueryService {

  private final BuncheolBookmarkRepository buncheolBookmarkRepository;
  private final BuncheolRepository buncheolRepository;
  private final GroupRepository groupRepository;
  private final BuncheolImageRepository buncheolImageRepository;
  private final BuncheolMemberNameResolver buncheolMemberNameResolver;
  private final UserFavoriteGroupRepository userFavoriteGroupRepository;

  @Transactional(readOnly = true)
  public List<MyBookmarkedBuncheolResponse> getMyBookmarkedBuncheols(
      final Long userId,
      final BookmarkSortOption sort,
      final boolean hideClosed,
      final boolean onlyFavoriteGroups) {
    // bookmarks 는 기본 LATEST(찜 등록 시각 내림차순) 으로 가져온다. DEADLINE 정렬은 아래에서 별도 처리.
    List<BuncheolBookmark> bookmarks =
        buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    if (bookmarks.isEmpty()) {
      return List.of();
    }

    List<Long> buncheolIds = bookmarks.stream().map(BuncheolBookmark::getBuncheolId).toList();
    Map<Long, Buncheol> buncheolById =
        buncheolRepository.findAllByIds(buncheolIds).stream()
            .collect(Collectors.toMap(Buncheol::getId, b -> b));

    Set<Long> favoriteGroupIds =
        onlyFavoriteGroups
            ? userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(UserFavoriteGroup::getGroupId)
                .collect(Collectors.toSet())
            : Set.of();

    List<BuncheolBookmark> filtered =
        bookmarks.stream()
            .filter(
                bm -> isVisible(bm, buncheolById, hideClosed, onlyFavoriteGroups, favoriteGroupIds))
            .collect(Collectors.toCollection(ArrayList::new));
    if (filtered.isEmpty()) {
      return List.of();
    }

    if (sort == BookmarkSortOption.DEADLINE) {
      // 마감 임박순: 모집중 분철을 마감 임박(deadline ASC)으로 먼저 보여주고, 그 뒤에 마감(CONFIRMED) 분철을
      // 마감일 내림차순(현재와 가까운 순, deadline DESC)으로 잇는다. 동일 키는 찜 id DESC.
      filtered.sort((a, b) -> compareByDeadlineGroup(a, b, buncheolById));
    }

    List<Long> groupIds =
        filtered.stream()
            .map(bm -> buncheolById.get(bm.getBuncheolId()).getGroupId())
            .distinct()
            .toList();
    Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));

    List<Long> visibleBuncheolIds =
        filtered.stream().map(BuncheolBookmark::getBuncheolId).distinct().toList();
    Map<Long, String> thumbnailByBuncheolId =
        buncheolImageRepository.findThumbnailsByBuncheolIds(visibleBuncheolIds).stream()
            .collect(Collectors.toMap(BuncheolImage::getBuncheolId, BuncheolImage::getImageUrl));

    Map<Long, List<String>> memberNamesByBuncheolId =
        buncheolMemberNameResolver.findNamesByBuncheolIds(visibleBuncheolIds);

    return filtered.stream()
        .map(
            bm ->
                toResponse(
                    bm,
                    buncheolById,
                    groupNameById,
                    thumbnailByBuncheolId,
                    memberNamesByBuncheolId))
        .toList();
  }

  // 마감 임박순 정렬 비교자: 모집중(RECRUITING) 우선 → 모집중은 deadline ASC, 비모집중(CONFIRMED/CANCELLED)은 deadline DESC, 동일 키는 찜 id DESC.
  // 전제: HOST_CANCELLED 는 isVisible 에서 제거됨 → RECRUITING/CONFIRMED/CANCELLED 만 진입(CANCELLED 도 비모집중으로 묶여 마감군과 함께 정렬).
  private int compareByDeadlineGroup(
      final BuncheolBookmark a,
      final BuncheolBookmark b,
      final Map<Long, Buncheol> buncheolById) {
    Buncheol ba = buncheolById.get(a.getBuncheolId());
    Buncheol bb = buncheolById.get(b.getBuncheolId());
    boolean aRecruiting = ba.getStatus() == BuncheolStatus.RECRUITING;
    boolean bRecruiting = bb.getStatus() == BuncheolStatus.RECRUITING;
    if (aRecruiting != bRecruiting) {
      return aRecruiting ? -1 : 1;
    }
    int byDeadline =
        aRecruiting
            ? ba.getDeadline().compareTo(bb.getDeadline())
            : bb.getDeadline().compareTo(ba.getDeadline());
    if (byDeadline != 0) {
      return byDeadline;
    }
    return Long.compare(b.getId(), a.getId());
  }

  private boolean isVisible(
      final BuncheolBookmark bookmark,
      final Map<Long, Buncheol> buncheolById,
      final boolean hideClosed,
      final boolean onlyFavoriteGroups,
      final Set<Long> favoriteGroupIds) {
    Buncheol buncheol = buncheolById.get(bookmark.getBuncheolId());
    if (buncheol == null) {
      // 분철이 hard delete 된 비정상 케이스 (FK CASCADE 가 막아주므로 정상 흐름엔 없음).
      return false;
    }
    // 개최자가 직접 취소한(HOST_CANCELLED) 분철만 항상 숨긴다. 인원 미달 자동취소(CANCELLED)는 찜 목록에도 노출한다.
    if (buncheol.getStatus() == BuncheolStatus.HOST_CANCELLED) {
      return false;
    }
    if (hideClosed && buncheol.getStatus() != BuncheolStatus.RECRUITING) {
      return false;
    }
    if (onlyFavoriteGroups && !favoriteGroupIds.contains(buncheol.getGroupId())) {
      return false;
    }
    return true;
  }

  private MyBookmarkedBuncheolResponse toResponse(
      final BuncheolBookmark bookmark,
      final Map<Long, Buncheol> buncheolById,
      final Map<Long, String> groupNameById,
      final Map<Long, String> thumbnailByBuncheolId,
      final Map<Long, List<String>> memberNamesByBuncheolId) {
    Buncheol buncheol = buncheolById.get(bookmark.getBuncheolId());
    return new MyBookmarkedBuncheolResponse(
        bookmark.getId(),
        buncheol.getId(),
        buncheol.getTitle(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        groupNameById.get(buncheol.getGroupId()),
        thumbnailByBuncheolId.get(buncheol.getId()),
        memberNamesByBuncheolId.getOrDefault(buncheol.getId(), List.of()));
  }
}
