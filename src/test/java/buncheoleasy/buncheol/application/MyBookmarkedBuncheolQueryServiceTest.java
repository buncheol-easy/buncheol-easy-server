package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyBookmarkedBuncheolQueryService 단위 테스트")
class MyBookmarkedBuncheolQueryServiceTest {

  private static final Long USER_ID = 1L;

  @InjectMocks private MyBookmarkedBuncheolQueryService myBookmarkedBuncheolQueryService;

  @Mock private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Mock private BuncheolRepository buncheolRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;

  @Nested
  @DisplayName("내 찜한 분철 목록 조회 테스트")
  class GetMyBookmarkedBuncheolsTest {

    @Test
    void 찜한_분철이_없으면_빈_리스트를_반환한다() {
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    void 분철_그룹_이미지를_조합해_카드_리스트를_반환한다() {
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2));

      Instant deadline1 = Instant.parse("2026-06-01T12:00:00Z");
      Instant deadline2 = Instant.parse("2026-06-15T12:00:00Z");
      Buncheol b1 = buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline1);
      Buncheol b2 = buncheol(20L, 200L, "분철 B", BuncheolStatus.CLOSED, deadline2);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L))).willReturn(List.of(b1, b2));

      given(groupRepository.findAllByIds(List.of(100L, 200L)))
          .willReturn(List.of(group(100L, "뉴진스"), group(200L, "에스파")));

      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of(image(10L, "https://cdn/img-a.jpg")));
      // 20L 은 이미지 없음 → thumbnailUrl null

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(USER_ID);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).bookmarkId()).isEqualTo(500L);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).title()).isEqualTo("분철 A");
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(result.get(0).deadline()).isEqualTo(deadline1);
      assertThat(result.get(0).groupName()).isEqualTo("뉴진스");
      assertThat(result.get(0).thumbnailUrl()).isEqualTo("https://cdn/img-a.jpg");

      assertThat(result.get(1).bookmarkId()).isEqualTo(501L);
      assertThat(result.get(1).buncheolId()).isEqualTo(20L);
      assertThat(result.get(1).status()).isEqualTo(BuncheolStatus.CLOSED);
      assertThat(result.get(1).groupName()).isEqualTo("에스파");
      assertThat(result.get(1).thumbnailUrl()).isNull();
    }
  }

  private BuncheolBookmark bookmark(Long id, Long userId, Long buncheolId) {
    BuncheolBookmark bookmark = newInstance(BuncheolBookmark.class);
    setField(bookmark, "id", id);
    setField(bookmark, "userId", userId);
    setField(bookmark, "buncheolId", buncheolId);
    return bookmark;
  }

  private Buncheol buncheol(
      Long id, Long groupId, String title, BuncheolStatus status, Instant deadline) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "status", status);
    setField(buncheol, "deadline", deadline);
    return buncheol;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private BuncheolImage image(Long buncheolId, String url) {
    BuncheolImage image = newInstance(BuncheolImage.class);
    setField(image, "buncheolId", buncheolId);
    setField(image, "imageUrl", url);
    return image;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
