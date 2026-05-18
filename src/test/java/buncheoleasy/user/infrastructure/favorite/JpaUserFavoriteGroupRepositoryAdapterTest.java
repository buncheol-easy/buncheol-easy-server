package buncheoleasy.user.infrastructure.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaUserFavoriteGroupRepositoryAdapter 테스트")
class JpaUserFavoriteGroupRepositoryAdapterTest {

  @Autowired private UserFavoriteGroupRepository userFavoriteGroupRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;
  private Long groupId;
  private Long otherGroupId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "user123");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "뉴진스");
    otherGroupId = TestGroupFixture.insertGroup(jdbcTemplate, "에스파");
  }

  @Nested
  @DisplayName("최애 저장")
  class SaveTest {

    @Test
    void 새_최애는_저장된다() {
      UserFavoriteGroup saved =
          userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
      em.flush();

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_user_group_조합으로_두_번_저장하면_BusinessException으로_변환된다() {
      userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
      em.flush();

      assertThatThrownBy(
              () -> {
                userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
                em.flush();
              })
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("existsByUserIdAndGroupId")
  class ExistsTest {

    @Test
    void 최애가_없으면_false를_반환한다() {
      assertThat(userFavoriteGroupRepository.existsByUserIdAndGroupId(userId, groupId)).isFalse();
    }

    @Test
    void 최애가_있으면_true를_반환한다() {
      userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
      em.flush();

      assertThat(userFavoriteGroupRepository.existsByUserIdAndGroupId(userId, groupId)).isTrue();
    }
  }

  @Nested
  @DisplayName("deleteByUserIdAndGroupId")
  class DeleteTest {

    @Test
    void 최애가_존재하면_삭제하고_1을_반환한다() {
      userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
      em.flush();

      int affected = userFavoriteGroupRepository.deleteByUserIdAndGroupId(userId, groupId);

      assertThat(affected).isEqualTo(1);
      assertThat(userFavoriteGroupRepository.existsByUserIdAndGroupId(userId, groupId)).isFalse();
    }

    @Test
    void 최애가_없으면_0을_반환한다() {
      int affected = userFavoriteGroupRepository.deleteByUserIdAndGroupId(userId, groupId);

      assertThat(affected).isZero();
    }
  }

  @Nested
  @DisplayName("findAllByUserIdOrderByCreatedAtDescIdDesc")
  class FindAllTest {

    @Test
    void 사용자의_최애를_최신_등록_순으로_반환한다() {
      userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
      userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, otherGroupId));
      em.flush();

      List<UserFavoriteGroup> result =
          userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getGroupId()).isEqualTo(otherGroupId);
      assertThat(result.get(1).getGroupId()).isEqualTo(groupId);
    }

    @Test
    void 최애가_없으면_빈_리스트를_반환한다() {
      List<UserFavoriteGroup> result =
          userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);

      assertThat(result).isEmpty();
    }
  }
}
