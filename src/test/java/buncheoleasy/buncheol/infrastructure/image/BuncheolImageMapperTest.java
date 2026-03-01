package buncheoleasy.buncheol.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.infrastructure.BuncheolMapper;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.infrastructure.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@ActiveProfiles("test")
@DisplayName("BuncheolImageMapper 테스트")
class BuncheolImageMapperTest {

    @Autowired
    private BuncheolImageMapper buncheolImageMapper;

    @Autowired
    private BuncheolMapper buncheolMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long buncheolId;

    @BeforeEach
    void setUp() {
        User host = User.create("KAKAO", "host123", "host@example.com");
        userMapper.insert(host);

        Buncheol buncheol = Buncheol.create(host.getId(), new BuncheolParams(
                null, "테스트 그룹", "제목", null, "앨범명", "스토어명",
                50_000L, LocalDateTime.now().plusDays(7), 7,
                3000, null, "국민은행", "123-456", "홍길동"
        ));
        buncheolMapper.insert(buncheol);
        buncheolId = buncheol.getId();
    }

    @Nested
    @DisplayName("분철 이미지 일괄 저장 테스트")
    class InsertAllTest {

        @Test
        void 이미지_한_장을_저장할_수_있다() {
            // given
            BuncheolImage image = BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg");

            // when & then
            assertThatCode(() -> buncheolImageMapper.insertAll(List.of(image)))
                    .doesNotThrowAnyException();
        }

        @Test
        void 이미지_여러_장을_한번에_저장할_수_있다() {
            // given
            List<BuncheolImage> images = List.of(
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg"),
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image3.jpg")
            );

            // when & then
            assertThatCode(() -> buncheolImageMapper.insertAll(images))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("유지 대상 외 이미지 삭제 테스트")
    class DeleteByBuncheolIdExcludingIdsTest {

        @Test
        void keepImageIds를_제외하고_이미지를_삭제한다() {
            // given
            List<BuncheolImage> images = List.of(
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg"),
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image3.jpg")
            );
            buncheolImageMapper.insertAll(images);

            List<Long> imageIds = jdbcTemplate.queryForList(
                    "SELECT id FROM buncheol_images WHERE buncheol_id = ? ORDER BY id",
                    Long.class,
                    buncheolId
            );
            Long keepId = imageIds.get(1);

            // when
            buncheolImageMapper.deleteByBuncheolIdExcludingIds(buncheolId, List.of(keepId));

            // then
            List<Long> remainingIds = jdbcTemplate.queryForList(
                    "SELECT id FROM buncheol_images WHERE buncheol_id = ? ORDER BY id",
                    Long.class,
                    buncheolId
            );
            assertThat(remainingIds).containsExactly(keepId);
        }

        @Test
        void keepImageIds가_비어있으면_해당_분철_이미지를_전체_삭제한다() {
            // given
            buncheolImageMapper.insertAll(List.of(
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image1.jpg"),
                    BuncheolImage.create(buncheolId, "https://cdn.example.com/image2.jpg")
            ));
            assertThat(countImagesByBuncheolId(buncheolId)).isEqualTo(2);

            // when
            buncheolImageMapper.deleteByBuncheolIdExcludingIds(buncheolId, List.of());

            // then
            assertThat(countImagesByBuncheolId(buncheolId)).isZero();
        }
    }

    private int countImagesByBuncheolId(final Long targetBuncheolId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM buncheol_images WHERE buncheol_id = ?",
                Integer.class,
                targetBuncheolId
        );
    }
}
