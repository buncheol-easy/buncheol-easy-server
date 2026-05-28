package buncheoleasy.user.infrastructure.recentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
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
@DisplayName("JpaUserRecentSearchRepositoryAdapter 테스트")
class JpaUserRecentSearchRepositoryAdapterTest {

  @Autowired private UserRecentSearchRepository userRecentSearchRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "rs-user");
  }

  @Nested
  @DisplayName("save")
  class SaveTest {

    @Test
    void 검색_이력이_저장된다() {
      UserRecentSearch saved =
          userRecentSearchRepository.save(UserRecentSearch.create(userId, "뉴진스"));
      em.flush();

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getKeyword()).isEqualTo("뉴진스");
      assertThat(saved.getCreatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("findTop7ByUserIdOrderByCreatedAtDescIdDesc")
  class FindTopTest {

    @Test
    void 최신_등록순으로_최대_7개를_반환한다() {
      for (int i = 1; i <= 8; i++) {
        userRecentSearchRepository.save(UserRecentSearch.create(userId, "kw" + i));
      }
      em.flush();

      List<UserRecentSearch> result =
          userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId);

      assertThat(result).hasSize(7);
      assertThat(result.get(0).getKeyword()).isEqualTo("kw8");
      assertThat(result.get(6).getKeyword()).isEqualTo("kw2");
    }

    @Test
    void 이력이_없으면_빈_리스트를_반환한다() {
      assertThat(userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("deleteByUserIdAndKeyword")
  class DeleteByKeywordTest {

    @Test
    void 동일_키워드_행만_삭제한다() {
      userRecentSearchRepository.save(UserRecentSearch.create(userId, "뉴진스"));
      userRecentSearchRepository.save(UserRecentSearch.create(userId, "에스파"));
      em.flush();

      int affected = userRecentSearchRepository.deleteByUserIdAndKeyword(userId, "뉴진스");
      em.flush();
      em.clear();

      assertThat(affected).isEqualTo(1);
      List<UserRecentSearch> remaining =
          userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId);
      assertThat(remaining)
          .extracting(UserRecentSearch::getKeyword)
          .containsExactly("에스파");
    }

    @Test
    void 동일_키워드가_없으면_0을_반환한다() {
      int affected = userRecentSearchRepository.deleteByUserIdAndKeyword(userId, "없는키워드");

      assertThat(affected).isZero();
    }
  }

  @Nested
  @DisplayName("findIdsToTrim + deleteAllByIdIn")
  class TrimTest {

    @Test
    void keep_개수_이후의_id를_반환한다() {
      for (int i = 1; i <= 10; i++) {
        userRecentSearchRepository.save(UserRecentSearch.create(userId, "kw" + i));
      }
      em.flush();

      List<Long> idsToTrim = userRecentSearchRepository.findIdsToTrim(userId, 7);

      assertThat(idsToTrim).hasSize(3);
    }

    @Test
    void keep_개수_이하면_빈_리스트를_반환한다() {
      for (int i = 1; i <= 5; i++) {
        userRecentSearchRepository.save(UserRecentSearch.create(userId, "kw" + i));
      }
      em.flush();

      assertThat(userRecentSearchRepository.findIdsToTrim(userId, 7)).isEmpty();
    }

    @Test
    void deleteAllByIdIn은_빈_리스트_호출도_안전하다() {
      userRecentSearchRepository.deleteAllByIdIn(List.of());

      assertThat(userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId))
          .isEmpty();
    }

    @Test
    void deleteAllByIdIn은_지정한_id만_삭제한다() {
      UserRecentSearch a = userRecentSearchRepository.save(UserRecentSearch.create(userId, "a"));
      UserRecentSearch b = userRecentSearchRepository.save(UserRecentSearch.create(userId, "b"));
      UserRecentSearch c = userRecentSearchRepository.save(UserRecentSearch.create(userId, "c"));
      em.flush();

      userRecentSearchRepository.deleteAllByIdIn(List.of(a.getId(), c.getId()));
      em.flush();
      em.clear();

      List<UserRecentSearch> remaining =
          userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId);
      assertThat(remaining).extracting(UserRecentSearch::getKeyword).containsExactly("b");
    }
  }
}
