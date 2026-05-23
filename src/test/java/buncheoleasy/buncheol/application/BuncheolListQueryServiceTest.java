package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolListQueryService 단위 테스트")
class BuncheolListQueryServiceTest {

  @InjectMocks private BuncheolListQueryService buncheolListQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Mock private BuncheolMemberNameResolver buncheolMemberNameResolver;

  @Nested
  @DisplayName("검색 결과 반환")
  class SearchResultTest {

    @Test
    void 결과가_요청_size_와_같거나_적으면_hasNext_false_이고_nextCursor_가_null() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      Buncheol b2 = buncheol(11L, 100L, "분철 B", Instant.parse("2026-05-14T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1, b2));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolMemberNameResolver.findNamesByBuncheolIds(List.of(10L, 11L)))
          .willReturn(Map.of(10L, List.of("민지"), 11L, List.of("하니")));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L, 11L)))
          .willReturn(Set.of(10L));

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 20);

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).id()).isEqualTo(10L);
      assertThat(result.items().get(0).bookmarked()).isTrue();
      assertThat(result.items().get(0).groupName()).isEqualTo("뉴진스");
      assertThat(result.items().get(0).memberNames()).containsExactly("민지");
      assertThat(result.items().get(1).bookmarked()).isFalse();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
    }

    @Test
    void size_plus1_fetch_되어_hasNext_true_시_마지막은_drop_되고_nextCursor_는_visible_의_마지막_항목() {
      Instant t1 = Instant.parse("2026-05-15T08:00:00Z");
      Instant t2 = Instant.parse("2026-05-14T08:00:00Z");
      Instant t3 = Instant.parse("2026-05-13T08:00:00Z"); // drop 대상
      Buncheol b1 = buncheol(10L, 100L, "분철 A", t1);
      Buncheol b2 = buncheol(11L, 100L, "분철 B", t2);
      Buncheol bDropped = buncheol(12L, 100L, "분철 C", t3);
      // requestedSize=2 → repo 는 size+1=3 으로 호출, 3개를 반환받음 → hasNext=true, visible=2개
      given(buncheolRepository.search(any(), any(), anyInt()))
          .willReturn(List.of(b1, b2, bDropped));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolMemberNameResolver.findNamesByBuncheolIds(List.of(10L, 11L)))
          .willReturn(Map.of());
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L, 11L)))
          .willReturn(Set.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 2);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(buncheolRepository).search(any(), any(), limitCaptor.capture());
      assertThat(limitCaptor.getValue()).isEqualTo(3);

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).id()).isEqualTo(10L);
      assertThat(result.items().get(1).id()).isEqualTo(11L);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.nextCursor()).isEqualTo(t2.toString() + "_11");
    }

    @Test
    void visible_이_비어있으면_그룹_멤버_북마크_조회를_모두_생략한다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 20);

      assertThat(result.items()).isEmpty();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      verifyNoInteractions(groupRepository, buncheolMemberNameResolver, buncheolBookmarkRepository);
    }
  }

  @Nested
  @DisplayName("비로그인 (userId=null) 처리")
  class AnonymousTest {

    @Test
    void userId_가_null_이면_bookmarked_가_모두_false_이고_북마크_조회_생략() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolMemberNameResolver.findNamesByBuncheolIds(List.of(10L))).willReturn(Map.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              null, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 20);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().get(0).bookmarked()).isFalse();
      verify(buncheolBookmarkRepository, never()).findBookmarkedBuncheolIds(anyLong(), anyList());
    }
  }

  @Nested
  @DisplayName("입력 정규화")
  class NormalizationTest {

    @Test
    void size_는_1_미만이면_1_로_50_초과면_50_으로_클램프되어_size_plus1_fetch_된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 0);
      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 999);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(buncheolRepository, org.mockito.Mockito.times(2))
          .search(any(), any(), limitCaptor.capture());
      assertThat(limitCaptor.getAllValues()).containsExactly(2, 51); // (1+1), (50+1)
    }

    @Test
    void keyword_가_blank_면_null_로_정규화되어_repo_에_전달된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "   "), Cursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().keyword()).isNull();
    }

    @Test
    void keyword_는_trim_되어_repo_에_전달된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "  뉴진스  "), Cursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().keyword()).isEqualTo("뉴진스");
    }
  }

  private Buncheol buncheol(Long id, Long groupId, String title, Instant createdAt) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "deadline", Instant.parse("2026-06-01T12:00:00Z"));
    // CreatedAtEntity#createdAt 은 부모 필드. setField 가 super 까지 탐색.
    setField(buncheol, "createdAt", createdAt);
    return buncheol;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
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
