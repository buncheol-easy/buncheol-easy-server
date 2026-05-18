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
import buncheoleasy.buncheol.dto.request.BookmarkSortOption;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
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
  @Mock private UserFavoriteGroupRepository userFavoriteGroupRepository;

  @Nested
  @DisplayName("내 찜한 분철 목록 조회 테스트")
  class GetMyBookmarkedBuncheolsTest {

    @Test
    void 찜한_분철이_없으면_빈_리스트를_반환한다() {
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result).isEmpty();
    }

    @Test
    void LATEST_정렬은_찜_등록_순서_그대로_반환된다() {
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

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).bookmarkId()).isEqualTo(500L);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(result.get(0).groupName()).isEqualTo("뉴진스");
      assertThat(result.get(0).thumbnailUrl()).isEqualTo("https://cdn/img-a.jpg");

      assertThat(result.get(1).bookmarkId()).isEqualTo(501L);
      assertThat(result.get(1).buncheolId()).isEqualTo(20L);
      assertThat(result.get(1).status()).isEqualTo(BuncheolStatus.CLOSED);
      assertThat(result.get(1).thumbnailUrl()).isNull();
    }

    @Test
    void DEADLINE_정렬은_분철_마감_임박순으로_재정렬된다() {
      // 찜 등록 순: bm1(분철 늦은 마감) → bm2(분철 빠른 마감) → bm3(분철 가장 빠른 마감)
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L);
      BuncheolBookmark bm3 = bookmark(502L, USER_ID, 30L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2, bm3));

      Buncheol b1 =
          buncheol(
              10L, 100L, "분철 A", BuncheolStatus.RECRUITING, Instant.parse("2026-07-01T00:00:00Z"));
      Buncheol b2 =
          buncheol(
              20L, 100L, "분철 B", BuncheolStatus.RECRUITING, Instant.parse("2026-06-15T00:00:00Z"));
      Buncheol b3 =
          buncheol(
              30L, 100L, "분철 C", BuncheolStatus.RECRUITING, Instant.parse("2026-06-01T00:00:00Z"));
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L)))
          .willReturn(List.of(b1, b2, b3));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(30L, 20L, 10L)))
          .willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.DEADLINE, false, false);

      assertThat(result).hasSize(3);
      // deadline ASC: C(06-01) → B(06-15) → A(07-01)
      assertThat(result.get(0).buncheolId()).isEqualTo(30L);
      assertThat(result.get(1).buncheolId()).isEqualTo(20L);
      assertThat(result.get(2).buncheolId()).isEqualTo(10L);
    }

    @Test
    void hideClosed_true_면_RECRUITING_이_아닌_분철은_제외된다() {
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L); // RECRUITING
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L); // CLOSED
      BuncheolBookmark bm3 = bookmark(502L, USER_ID, 30L); // CANCELLED
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2, bm3));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol b1 = buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline);
      Buncheol b2 = buncheol(20L, 100L, "분철 B", BuncheolStatus.CLOSED, deadline);
      Buncheol b3 = buncheol(30L, 100L, "분철 C", BuncheolStatus.CANCELLED, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L)))
          .willReturn(List.of(b1, b2, b3));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(10L))).willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, true, false);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void onlyFavoriteGroups_true_면_사용자_최애_그룹의_분철만_포함된다() {
      BuncheolBookmark bmFav = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bmNonFav = bookmark(501L, USER_ID, 20L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bmFav, bmNonFav));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol bFav = buncheol(10L, 100L, "최애 그룹 분철", BuncheolStatus.RECRUITING, deadline);
      Buncheol bNonFav = buncheol(20L, 200L, "다른 그룹 분철", BuncheolStatus.RECRUITING, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L))).willReturn(List.of(bFav, bNonFav));

      // 사용자의 최애: 그룹 100 만
      given(userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(favorite(700L, USER_ID, 100L)));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(10L))).willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, true);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).groupName()).isEqualTo("뉴진스");
    }

    @Test
    void 필터로_모두_제외되면_빈_리스트를_반환한다() {
      BuncheolBookmark bm = bookmark(500L, USER_ID, 10L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol closed = buncheol(10L, 100L, "분철", BuncheolStatus.CLOSED, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(closed));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, true, false);

      assertThat(result).isEmpty();
    }
  }

  private UserFavoriteGroup favorite(Long id, Long userId, Long groupId) {
    UserFavoriteGroup favorite = newInstance(UserFavoriteGroup.class);
    setField(favorite, "id", id);
    setField(favorite, "userId", userId);
    setField(favorite, "groupId", groupId);
    return favorite;
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
