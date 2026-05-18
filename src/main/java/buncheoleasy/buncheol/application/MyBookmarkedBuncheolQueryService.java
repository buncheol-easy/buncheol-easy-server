package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @Transactional(readOnly = true)
  public List<MyBookmarkedBuncheolResponse> getMyBookmarkedBuncheols(final Long userId) {
    List<BuncheolBookmark> bookmarks =
        buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    if (bookmarks.isEmpty()) {
      return List.of();
    }

    List<Long> buncheolIds = bookmarks.stream().map(BuncheolBookmark::getBuncheolId).toList();
    Map<Long, Buncheol> buncheolById =
        buncheolRepository.findAllByIds(buncheolIds).stream()
            .collect(Collectors.toMap(Buncheol::getId, b -> b));

    List<Long> groupIds =
        buncheolIds.stream()
            .map(buncheolById::get)
            .filter(Objects::nonNull)
            .map(Buncheol::getGroupId)
            .distinct()
            .toList();
    Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));

    Map<Long, String> thumbnailByBuncheolId =
        buncheolImageRepository.findFirstByBuncheolIds(buncheolIds).stream()
            .collect(Collectors.toMap(BuncheolImage::getBuncheolId, BuncheolImage::getImageUrl));

    return bookmarks.stream()
        .map(bm -> toResponse(bm, buncheolById, groupNameById, thumbnailByBuncheolId))
        .filter(Objects::nonNull)
        .toList();
  }

  private MyBookmarkedBuncheolResponse toResponse(
      final BuncheolBookmark bookmark,
      final Map<Long, Buncheol> buncheolById,
      final Map<Long, String> groupNameById,
      final Map<Long, String> thumbnailByBuncheolId) {
    Buncheol buncheol = buncheolById.get(bookmark.getBuncheolId());
    if (buncheol == null) {
      // 분철이 hard delete 된 비정상 케이스 (FK CASCADE 가 막아주므로 정상 흐름엔 없음). 방어적으로 skip.
      return null;
    }
    return new MyBookmarkedBuncheolResponse(
        bookmark.getId(),
        buncheol.getId(),
        buncheol.getTitle(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        groupNameById.get(buncheol.getGroupId()),
        thumbnailByBuncheolId.get(buncheol.getId()));
  }
}
