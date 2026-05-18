package buncheoleasy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import buncheoleasy.user.dto.response.MyFavoriteGroupResponse;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyFavoriteGroupQueryService 단위 테스트")
class MyFavoriteGroupQueryServiceTest {

  private static final Long USER_ID = 1L;

  @InjectMocks private MyFavoriteGroupQueryService myFavoriteGroupQueryService;

  @Mock private UserFavoriteGroupRepository userFavoriteGroupRepository;
  @Mock private GroupRepository groupRepository;

  @Nested
  @DisplayName("내 최애 그룹 목록 조회 테스트")
  class GetMyFavoriteGroupsTest {

    @Test
    void 최애_그룹이_없으면_빈_리스트를_반환한다() {
      given(userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of());

      List<MyFavoriteGroupResponse> result =
          myFavoriteGroupQueryService.getMyFavoriteGroups(USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    void 등록순으로_그룹_카드를_반환한다() {
      UserFavoriteGroup f1 = favorite(700L, USER_ID, 100L);
      UserFavoriteGroup f2 = favorite(701L, USER_ID, 200L);
      given(userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(f1, f2));

      given(groupRepository.findAllByIds(List.of(100L, 200L)))
          .willReturn(
              List.of(
                  group(100L, "뉴진스", "https://cdn/newjeans.jpg"),
                  group(200L, "에스파", null)));

      List<MyFavoriteGroupResponse> result =
          myFavoriteGroupQueryService.getMyFavoriteGroups(USER_ID);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).favoriteId()).isEqualTo(700L);
      assertThat(result.get(0).groupId()).isEqualTo(100L);
      assertThat(result.get(0).name()).isEqualTo("뉴진스");
      assertThat(result.get(0).imageUrl()).isEqualTo("https://cdn/newjeans.jpg");

      assertThat(result.get(1).favoriteId()).isEqualTo(701L);
      assertThat(result.get(1).groupId()).isEqualTo(200L);
      assertThat(result.get(1).name()).isEqualTo("에스파");
      assertThat(result.get(1).imageUrl()).isNull();
    }
  }

  private UserFavoriteGroup favorite(Long id, Long userId, Long groupId) {
    UserFavoriteGroup f = newInstance(UserFavoriteGroup.class);
    setField(f, "id", id);
    setField(f, "userId", userId);
    setField(f, "groupId", groupId);
    return f;
  }

  private Group group(Long id, String name, String image) {
    Group g = newInstance(Group.class);
    setField(g, "id", id);
    setField(g, "name", name);
    setField(g, "image", image);
    return g;
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
