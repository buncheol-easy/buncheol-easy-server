package buncheoleasy.buncheol.infrastructure.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@DisplayName("JpaBuncheolBookmarkRepositoryAdapter 테스트")
class JpaBuncheolBookmarkRepositoryAdapterTest {

  @Autowired private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;
  private Long buncheolId;
  private Long otherBuncheolId;

  @BeforeEach
  void setUp() {
    Long hostId = TestUserFixture.insertUser(jdbcTemplate, "host123");
    userId = TestUserFixture.insertUser(jdbcTemplate, "user123");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹");

    buncheolId = createBuncheol(hostId, groupId, "분철 A");
    otherBuncheolId = createBuncheol(hostId, groupId, "분철 B");
  }

  private Long createBuncheol(Long hostId, Long groupId, String title) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, title, null, "스토어명", deadline, 3000, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    return buncheol.getId();
  }

  @Nested
  @DisplayName("찜 저장")
  class SaveTest {

    @Test
    void 새_찜은_저장된다() {
      BuncheolBookmark saved =
          buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
      em.flush();

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_user_buncheol_조합으로_두_번_저장하면_BusinessException으로_변환된다() {
      buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
      em.flush();

      assertThatThrownBy(
              () -> {
                buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
                em.flush();
              })
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_BOOKMARK_ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("existsByUserIdAndBuncheolId")
  class ExistsTest {

    @Test
    void 찜이_없으면_false를_반환한다() {
      assertThat(buncheolBookmarkRepository.existsByUserIdAndBuncheolId(userId, buncheolId))
          .isFalse();
    }

    @Test
    void 찜이_있으면_true를_반환한다() {
      buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
      em.flush();

      assertThat(buncheolBookmarkRepository.existsByUserIdAndBuncheolId(userId, buncheolId))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("deleteByUserIdAndBuncheolId")
  class DeleteTest {

    @Test
    void 찜이_존재하면_삭제하고_1을_반환한다() {
      buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
      em.flush();

      int affected = buncheolBookmarkRepository.deleteByUserIdAndBuncheolId(userId, buncheolId);

      assertThat(affected).isEqualTo(1);
      assertThat(buncheolBookmarkRepository.existsByUserIdAndBuncheolId(userId, buncheolId))
          .isFalse();
    }

    @Test
    void 찜이_없으면_0을_반환한다() {
      int affected = buncheolBookmarkRepository.deleteByUserIdAndBuncheolId(userId, buncheolId);

      assertThat(affected).isZero();
    }
  }

  @Nested
  @DisplayName("findAllByUserIdOrderByCreatedAtDescIdDesc")
  class FindAllTest {

    @Test
    void 사용자의_찜을_최신_등록_순으로_반환한다() {
      buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
      buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, otherBuncheolId));
      em.flush();

      List<BuncheolBookmark> result =
          buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);

      assertThat(result).hasSize(2);
      // tie-breaker: id DESC 라 나중에 저장된 otherBuncheolId 가 먼저
      assertThat(result.get(0).getBuncheolId()).isEqualTo(otherBuncheolId);
      assertThat(result.get(1).getBuncheolId()).isEqualTo(buncheolId);
    }

    @Test
    void 찜이_없으면_빈_리스트를_반환한다() {
      List<BuncheolBookmark> result =
          buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);

      assertThat(result).isEmpty();
    }
  }
}
