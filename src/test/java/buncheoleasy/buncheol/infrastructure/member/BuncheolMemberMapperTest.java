package buncheoleasy.buncheol.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
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
@DisplayName("BuncheolMemberMapper 테스트")
class BuncheolMemberMapperTest {

    @Autowired
    private BuncheolMemberMapper buncheolMemberMapper;

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
    @DisplayName("분철 멤버 일괄 저장 테스트")
    class InsertAllTest {

        @Test
        void 입찰_불가_멤버를_저장할_수_있다() {
            // given
            BuncheolMember member = BuncheolMember.create(buncheolId, null, "멤버A", 50_000L, false, null);

            // when & then
            assertThatCode(() -> buncheolMemberMapper.insertAll(List.of(member)))
                    .doesNotThrowAnyException();
        }

        @Test
        void 입찰_가능_멤버를_저장할_수_있다() {
            // given
            BuncheolMember member = BuncheolMember.create(buncheolId, null, "멤버B", 50_000L, true, 10_000L);

            // when & then
            assertThatCode(() -> buncheolMemberMapper.insertAll(List.of(member)))
                    .doesNotThrowAnyException();
        }

        @Test
        void 여러_멤버를_한번에_저장할_수_있다() {
            // given
            List<BuncheolMember> members = List.of(
                    BuncheolMember.create(buncheolId, null, "멤버A", 50_000L, false, null),
                    BuncheolMember.create(buncheolId, null, "멤버B", 30_000L, true, 5_000L),
                    BuncheolMember.create(buncheolId, null, "멤버C", 20_000L, false, null)
            );

            // when & then
            assertThatCode(() -> buncheolMemberMapper.insertAll(members))
                    .doesNotThrowAnyException();
        }
    }
}
