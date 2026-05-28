package buncheoleasy.user.application.recentsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRecentSearchQueryService 단위 테스트")
class UserRecentSearchQueryServiceTest {

  private static final Long USER_ID = 1L;

  @InjectMocks private UserRecentSearchQueryService queryService;

  @Mock private UserRecentSearchRepository userRecentSearchRepository;

  @Test
  void userId_가_null_이면_빈_리스트를_반환하고_저장소를_조회하지_않는다() {
    List<RecentSearchResponse> result = queryService.getRecent(null);

    assertThat(result).isEmpty();
    verifyNoInteractions(userRecentSearchRepository);
  }

  @Test
  void 사용자의_최근_검색어를_최신순으로_매핑한다() {
    UserRecentSearch s1 = entity(11L, "뉴진스");
    UserRecentSearch s2 = entity(12L, "민지");
    given(userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
        .willReturn(List.of(s1, s2));

    List<RecentSearchResponse> result = queryService.getRecent(USER_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(11L);
    assertThat(result.get(0).keyword()).isEqualTo("뉴진스");
    assertThat(result.get(1).keyword()).isEqualTo("민지");
  }

  @Test
  void 이력이_없으면_빈_리스트를_반환한다() {
    given(userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
        .willReturn(List.of());

    assertThat(queryService.getRecent(USER_ID)).isEmpty();
  }

  private UserRecentSearch entity(Long id, String keyword) {
    UserRecentSearch entity = UserRecentSearch.create(USER_ID, keyword);
    setField(entity, "id", id);
    return entity;
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
