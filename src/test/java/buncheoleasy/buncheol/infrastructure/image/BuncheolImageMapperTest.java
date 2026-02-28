package buncheoleasy.buncheol.infrastructure.image;

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
}
