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

    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 3000, null),
            Instant.now());
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

  @Nested
  @DisplayName("단일 분철 이미지 전체 조회 테스트")
  class FindAllByBuncheolIdOrderByIdAscTest {

    @Test
    void 등록_순서대로_이미지_전체를_조회한다() {
      buncheolImageRepository.saveAll(
          List.of(
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg"),
              BuncheolImage.create(buncheolId, "https://cdn.example.com/image3.jpg")));
      em.flush();
      em.clear();

      List<BuncheolImage> result =
          buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);

      assertThat(result)
          .extracting(BuncheolImage::getImageUrl)
          .containsExactly(
              "https://cdn.example.com/image1.jpg",
              "https://cdn.example.com/image2.jpg",
              "https://cdn.example.com/image3.jpg");
    }

    @Test
    void 이미지가_없으면_빈_리스트를_반환한다() {
      List<BuncheolImage> result =
          buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);

      assertThat(result).isEmpty();
    }

    @Test
    void 다른_분철의_이미지는_포함하지_않는다() {
      Long anotherHostId = TestUserFixture.insertUser(jdbcTemplate, "host456");
      Buncheol another =
          Buncheol.create(
              anotherHostId,
              new BuncheolParams(
                  TestGroupFixture.insertGroup(jdbcTemplate, "다른 그룹"),
                  "다른 제목",
                  null,
                  "다른 스토어",
                  Instant.now().plus(7, ChronoUnit.DAYS),
                  3000,
                  null),
              Instant.now());
      buncheolRepository.save(another);
      em.flush();

      buncheolImageRepository.saveAll(
          List.of(BuncheolImage.create(buncheolId, "https://cdn.example.com/own.jpg")));
      buncheolImageRepository.saveAll(
          List.of(BuncheolImage.create(another.getId(), "https://cdn.example.com/other.jpg")));
      em.flush();
      em.clear();

      List<BuncheolImage> result =
          buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);

      assertThat(result)
          .extracting(BuncheolImage::getImageUrl)
          .containsExactly("https://cdn.example.com/own.jpg");
    }
  }

  private int countImagesByBuncheolId(final Long targetBuncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheol_images WHERE buncheol_id = ?",
        Integer.class,
        targetBuncheolId);
  }
}
