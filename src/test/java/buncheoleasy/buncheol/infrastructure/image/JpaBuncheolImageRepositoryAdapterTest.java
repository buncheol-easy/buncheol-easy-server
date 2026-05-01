package buncheoleasy.buncheol.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
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
@DisplayName("JpaBuncheolImageRepositoryAdapter 테스트")
class JpaBuncheolImageRepositoryAdapterTest {

  @Autowired private BuncheolImageRepository buncheolImageRepository;

  @Autowired private BuncheolRepository buncheolRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long buncheolId;

  @BeforeEach
  void setUp() {
    Long hostId = TestUserFixture.insertUser(jdbcTemplate, "host123");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹");

    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(
                groupId, "제목", null, "스토어명", LocalDateTime.now().plusDays(7), 7, 3000, null));
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    buncheolId = buncheol.getId();
  }

  @Nested
  @DisplayName("분철 이미지 일괄 저장 테스트")
  class SaveAllTest {

    @Test
    void 이미지_한_장을_저장할_수_있다() {
      BuncheolImage image = BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg");

      assertThatCode(() -> buncheolImageRepository.saveAll(List.of(image)))
          .doesNotThrowAnyException();
    }

    @Test
    void 이미지_여러_장을_한번에_저장할_수_있다() {
      List<BuncheolImage> images =
          List.of(
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image3.jpg"));

      assertThatCode(() -> buncheolImageRepository.saveAll(images)).doesNotThrowAnyException();
    }

    @Test
    void 빈_리스트를_저장해도_예외가_발생하지_않는다() {
      assertThatCode(() -> buncheolImageRepository.saveAll(List.of())).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("유지 대상 외 이미지 삭제 테스트")
  class DeleteByBuncheolIdExcludingIdsTest {

    @Test
    void keepImageIds를_제외하고_이미지를_삭제한다() {
      List<BuncheolImage> images =
          List.of(
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image3.jpg"));
      buncheolImageRepository.saveAll(images);
      em.flush();

      List<Long> imageIds =
          jdbcTemplate.queryForList(
              "SELECT id FROM buncheol_images WHERE buncheol_id = ? ORDER BY id",
              Long.class,
              buncheolId);
      Long keepId = imageIds.get(1);

      buncheolImageRepository.deleteByBuncheolIdExcludingIds(buncheolId, List.of(keepId));

      List<Long> remainingIds =
          jdbcTemplate.queryForList(
              "SELECT id FROM buncheol_images WHERE buncheol_id = ? ORDER BY id",
              Long.class,
              buncheolId);
      assertThat(remainingIds).containsExactly(keepId);
    }

    @Test
    void keepImageIds가_비어있으면_해당_분철_이미지를_전체_삭제한다() {
      buncheolImageRepository.saveAll(
          List.of(
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg")));
      em.flush();
      assertThat(countImagesByBuncheolId(buncheolId)).isEqualTo(2);

      buncheolImageRepository.deleteByBuncheolIdExcludingIds(buncheolId, List.of());

      assertThat(countImagesByBuncheolId(buncheolId)).isZero();
    }
  }

  private int countImagesByBuncheolId(final Long targetBuncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheol_images WHERE buncheol_id = ?",
        Integer.class,
        targetBuncheolId);
  }
}
