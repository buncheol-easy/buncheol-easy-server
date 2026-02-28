package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.infrastructure.UserMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@ActiveProfiles("test")
@DisplayName("BuncheolMapper 테스트")
class BuncheolMapperTest {

    @Autowired
    private BuncheolMapper buncheolMapper;

    @Autowired
    private UserMapper userMapper;

    private Long hostId;

    @BeforeEach
    void setUp() {
        User host = User.create("KAKAO", "host123", "host@example.com");
        userMapper.insert(host);
        hostId = host.getId();
    }

    private BuncheolParams validParams(String groupName) {
        return new BuncheolParams(
                null,
                groupName,
                "테스트 분철 제목",
                "분철 설명입니다.",
                "공식 앨범",
                "공식 스토어",
                50_000L,
                LocalDateTime.now().plusDays(7),
                7,
                3000,
                null,
                "국민은행",
                "123-456-789012",
                "홍길동"
        );
    }

    @Nested
    @DisplayName("분철 저장 테스트")
    class InsertTest {

        @Test
        void 분철을_저장할_수_있다() {
            // given
            Buncheol buncheol = Buncheol.create(hostId, validParams("테스트 그룹"));

            // when
            buncheolMapper.insert(buncheol);

            // then
            assertThat(buncheol.getId()).isNotNull();
            assertThat(buncheol.getId()).isPositive();
        }

        @Test
        void 저장된_분철의_초기_상태는_RECRUITING이다() {
            // given
            Buncheol buncheol = Buncheol.create(hostId, validParams("테스트 그룹"));

            // when
            buncheolMapper.insert(buncheol);

            // then
            assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
        }

        @Test
        void gs25_배송비만_설정하여_저장할_수_있다() {
            // given
            BuncheolParams params = new BuncheolParams(
                    null, "그룹명", "제목", null, "앨범명", "스토어명",
                    30_000L, LocalDateTime.now().plusDays(7), 5,
                    2500, null,
                    "우리은행", "1002-123-456789", "홍길동"
            );
            Buncheol buncheol = Buncheol.create(hostId, params);

            // when & then
            buncheolMapper.insert(buncheol);
            assertThat(buncheol.getId()).isNotNull();
        }

        @Test
        void cu_배송비만_설정하여_저장할_수_있다() {
            // given
            BuncheolParams params = new BuncheolParams(
                    null, "그룹명", "제목", null, "앨범명", "스토어명",
                    30_000L, LocalDateTime.now().plusDays(7), 5,
                    null, 2000,
                    "하나은행", "1002-123-456789", "홍길동"
            );
            Buncheol buncheol = Buncheol.create(hostId, params);

            // when & then
            buncheolMapper.insert(buncheol);
            assertThat(buncheol.getId()).isNotNull();
        }
    }
}
