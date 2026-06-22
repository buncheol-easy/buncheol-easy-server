package buncheoleasy.buncheol.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
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
@DisplayName("JpaBuncheolMemberRepositoryAdapter 테스트")
class JpaBuncheolMemberRepositoryAdapterTest {

  @Autowired private BuncheolMemberRepository buncheolMemberRepository;

  @Autowired private BuncheolRepository buncheolRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long buncheolId;
  private Long memberId;
  private Long memberId2;
  private Long memberId3;

  @BeforeEach
  void setUp() {
    Long hostId = TestUserFixture.insertUser(jdbcTemplate, "host123");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹");
    memberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "테스트 멤버1");
    memberId2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "테스트 멤버2");
    memberId3 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "테스트 멤버3");

    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 1, 3000, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    buncheolId = buncheol.getId();
  }

  @Nested
  @DisplayName("분철 멤버 일괄 저장 테스트")
  class SaveAllTest {

    @Test
    void 단일_멤버를_저장할_수_있다() {
      BuncheolMember member = BuncheolMember.create(buncheolId, memberId, 50_000L);

      assertThatCode(() -> buncheolMemberRepository.saveAll(List.of(member)))
          .doesNotThrowAnyException();
    }

    @Test
    void 여러_멤버를_한번에_저장할_수_있다() {
      List<BuncheolMember> members =
          List.of(
              BuncheolMember.create(buncheolId, memberId, 50_000L),
              BuncheolMember.create(buncheolId, memberId2, 5_000L),
              BuncheolMember.create(buncheolId, memberId3, 20_000L));

      assertThatCode(() -> buncheolMemberRepository.saveAll(members)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("분철 멤버 삭제 테스트")
  class DeleteAllByBuncheolIdTest {

    @Test
    void 특정_분철의_멤버를_전체_삭제할_수_있다() {
      List<BuncheolMember> members =
          List.of(
              BuncheolMember.create(buncheolId, memberId, 50_000L),
              BuncheolMember.create(buncheolId, memberId2, 30_000L));
      buncheolMemberRepository.saveAll(members);
      em.flush();
      assertThat(countMembersByBuncheolId(buncheolId)).isEqualTo(2);

      buncheolMemberRepository.deleteAllByBuncheolId(buncheolId);

      assertThat(countMembersByBuncheolId(buncheolId)).isZero();
    }
  }

  private int countMembersByBuncheolId(final Long targetBuncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheol_members WHERE buncheol_id = ?",
        Integer.class,
        targetBuncheolId);
  }
}
