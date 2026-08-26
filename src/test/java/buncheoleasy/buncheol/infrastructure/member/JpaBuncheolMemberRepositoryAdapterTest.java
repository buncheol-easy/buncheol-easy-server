package buncheoleasy.buncheol.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.SlotAccessType;
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
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 1, 3000, null, FlowType.LEGACY, null),
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
  @DisplayName("슬롯 접근 정책 전환 CAS 테스트")
  class ChangeAccessTypeTest {

    private Long saveSlot() {
      BuncheolMember member = BuncheolMember.create(buncheolId, memberId, 0L);
      buncheolMemberRepository.saveAll(List.of(member));
      em.flush();
      em.clear();
      return member.getId();
    }

    @Test
    void 참여가_없는_슬롯은_코드_참여로_전환된다() {
      Long slotId = saveSlot();

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, SlotAccessType.CODE_ONLY))
          .isTrue();

      em.clear();
      assertThat(
              buncheolMemberRepository
                  .findByIdAndBuncheolId(slotId, buncheolId)
                  .orElseThrow()
                  .getAccessType())
          .isEqualTo(SlotAccessType.CODE_ONLY);
    }

    @Test
    void 활성_참여가_있는_슬롯은_전환되지_않는다() {
      Long slotId = saveSlot();
      insertActiveParticipation(slotId);

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, SlotAccessType.CODE_ONLY))
          .isFalse();
    }

    @Test
    void 취소된_참여만_있는_슬롯은_전환된다() {
      Long slotId = saveSlot();
      insertCancelledParticipation(slotId);

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, SlotAccessType.CODE_ONLY))
          .isTrue();
    }

    @Test
    void 다른_분철의_슬롯은_전환되지_않는다() {
      Long slotId = saveSlot();

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId + 9_999L, SlotAccessType.CODE_ONLY))
          .isFalse();
    }

    private void insertActiveParticipation(final Long slotId) {
      insertParticipation(slotId, "CONFIRMED");
    }

    private void insertCancelledParticipation(final Long slotId) {
      insertParticipation(slotId, "CANCELLED");
    }

    private void insertParticipation(final Long slotId, final String status) {
      Long participantId = TestUserFixture.insertUser(jdbcTemplate, status.substring(0, 4));
      jdbcTemplate.update(
          "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, amount,"
              + " shipping_fee, refund_bank, refund_account, refund_holder, status, flow_type)"
              + " VALUES (?, ?, ?, 0, 0, NULL, NULL, NULL, ?, 'LEGACY')",
          buncheolId,
          slotId,
          participantId,
          status);
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
