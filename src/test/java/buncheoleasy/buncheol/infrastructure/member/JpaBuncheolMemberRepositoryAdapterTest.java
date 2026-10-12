package buncheoleasy.buncheol.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
                  slotId, buncheolId, BuncheolMemberAccessType.CODE_ONLY))
          .isTrue();

      em.clear();
      assertThat(
              buncheolMemberRepository
                  .findByIdAndBuncheolId(slotId, buncheolId)
                  .orElseThrow()
                  .getAccessType())
          .isEqualTo(BuncheolMemberAccessType.CODE_ONLY);
    }

    @Test
    void 활성_참여가_있는_슬롯은_전환되지_않는다() {
      Long slotId = saveSlot();
      insertActiveParticipation(slotId);

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, BuncheolMemberAccessType.CODE_ONLY))
          .isFalse();
    }

    @Test
    void 취소된_참여만_있는_슬롯은_전환된다() {
      Long slotId = saveSlot();
      insertCancelledParticipation(slotId);

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, BuncheolMemberAccessType.CODE_ONLY))
          .isTrue();
    }

    /**
     * 이 CAS 는 {@code updated_at} 을 명시 대입하지 않는다 — bulk UPDATE 라 {@code @PreUpdate} 도 타지 않으므로,
     * 갱신 시각은 오직 컬럼의 {@code ON UPDATE CURRENT_TIMESTAMP} 로만 남는다. 시계 해상도에 의존하지 않도록
     * 과거 시각을 심어 두고 그 값이 밀려났는지로 판정한다.
     */
    @Test
    void 명시_대입이_없어도_DB_가_updated_at_을_갱신한다() {
      Long slotId = saveSlot();
      Timestamp sentinel = Timestamp.valueOf("2000-01-01 00:00:00");
      jdbcTemplate.update(
          "UPDATE buncheol_members SET updated_at = ? WHERE id = ?", sentinel, slotId);

      buncheolMemberRepository.changeAccessTypeIfUnoccupied(
          slotId, buncheolId, BuncheolMemberAccessType.CODE_ONLY);

      assertThat(readUpdatedAt(slotId))
          .as("updated_at 에 ON UPDATE CURRENT_TIMESTAMP 가 빠졌다 — CAS 로 바꾼 행의 갱신 시각이 멈춘다")
          .isAfter(sentinel);
    }

    private Timestamp readUpdatedAt(final Long slotId) {
      return jdbcTemplate.queryForObject(
          "SELECT updated_at FROM buncheol_members WHERE id = ?", Timestamp.class, slotId);
    }

    @Test
    void 다른_분철의_슬롯은_전환되지_않는다() {
      Long slotId = saveSlot();

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId + 9_999L, BuncheolMemberAccessType.CODE_ONLY))
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

  @Nested
  @DisplayName("참조 없는 자리 삭제 테스트")
  class DeleteIfUnreferencedTest {

    @Test
    void 참여도_참여_코드도_없는_자리는_지워진다() {
      Long slotId = saveSlot(memberId);
      Long otherSlotId = saveSlot(memberId2);

      assertThat(buncheolMemberRepository.deleteIfUnreferenced(slotId, buncheolId)).isTrue();

      assertThat(slotExists(slotId)).isFalse();
      assertThat(slotExists(otherSlotId)).isTrue();
    }

    // 🔴 취소(CANCELLED)도 막는다 — 취소된 참여 행도 FK 로 자리를 가리켜, 지우려 하면 DB 가 거부한다.
    @ParameterizedTest
    @ValueSource(
        strings = {"APPLIED", "AWAITING_PAYMENT", "PAYMENT_SENT", "CONFIRMED", "CANCELLED"})
    void 참여_행이_하나라도_있으면_상태와_무관하게_지우지_않는다(final String status) {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, status);

      assertThat(buncheolMemberRepository.deleteIfUnreferenced(slotId, buncheolId)).isFalse();

      assertThat(slotExists(slotId)).isTrue();
    }

    // participation_codes.buncheol_member_id 도 ON DELETE 없는 FK 다. 빠뜨리면 DELETE 가 FK 로 터진다.
    @Test
    void 참여_코드가_붙은_자리는_지우지_않는다() {
      Long slotId = saveSlot(memberId);
      jdbcTemplate.update(
          "INSERT INTO participation_codes (code, buncheol_id, buncheol_member_id, expires_at)"
              + " VALUES ('CODE2345', ?, ?, ?)",
          buncheolId,
          slotId,
          Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));

      assertThat(buncheolMemberRepository.deleteIfUnreferenced(slotId, buncheolId)).isFalse();

      assertThat(slotExists(slotId)).isTrue();
    }

    @Test
    void 다른_분철의_자리는_지우지_않는다() {
      Long slotId = saveSlot(memberId);

      assertThat(buncheolMemberRepository.deleteIfUnreferenced(slotId, buncheolId + 9_999L))
          .isFalse();

      assertThat(slotExists(slotId)).isTrue();
    }

    // 조건부 DELETE 가 뚫려도 DB 가 마지막 선이다 — 참여 행이 가리키는 자리는 FK 가 삭제를 거부한다.
    @Test
    void 참여_행이_가리키는_자리는_FK_가_삭제를_거부한다() {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, "CANCELLED");

      assertThatThrownBy(
              () -> jdbcTemplate.update("DELETE FROM buncheol_members WHERE id = ?", slotId))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("자리 잠금 조회 테스트")
  class FindAllByBuncheolIdForUpdateTest {

    @Test
    void 그_분철의_자리만_돌려준다() {
      Long slotId = saveSlot(memberId);
      Long slotId2 = saveSlot(memberId2);
      Long otherBuncheolId = saveOtherBuncheol();
      buncheolMemberRepository.saveAll(
          List.of(BuncheolMember.create(otherBuncheolId, memberId3, 1_000L)));
      em.flush();
      em.clear();

      assertThat(buncheolMemberRepository.findAllByBuncheolIdForUpdate(buncheolId))
          .extracting(BuncheolMember::getId)
          .containsExactlyInAnyOrder(slotId, slotId2);
    }
  }

  @Nested
  @DisplayName("자리 하나 저장 테스트")
  class SaveTest {

    @Test
    void 새_자리를_저장하면_id_가_채워지고_선착순이다() {
      BuncheolMember saved =
          buncheolMemberRepository.save(BuncheolMember.create(buncheolId, memberId, 12_300L));

      assertThat(saved.getId()).isNotNull();
      em.clear();
      BuncheolMember loaded =
          buncheolMemberRepository.findByIdAndBuncheolId(saved.getId(), buncheolId).orElseThrow();
      assertThat(loaded.getPrice()).isEqualTo(12_300L);
      assertThat(loaded.getAccessType()).isEqualTo(BuncheolMemberAccessType.OPEN);
    }

    // 사전 검사를 빠져나간 경합의 마지막 선 — 500 이 아니라 중복 코드로 떨어져야 한다.
    @Test
    void 같은_분철에_같은_멤버가_이미_있으면_중복_코드로_거부한다() {
      saveSlot(memberId);

      assertThatThrownBy(
              () ->
                  buncheolMemberRepository.save(BuncheolMember.create(buncheolId, memberId, 0L)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
    }

    @Test
    void 분철_안_멤버_존재_여부를_돌려준다() {
      saveSlot(memberId);

      assertThat(buncheolMemberRepository.existsByBuncheolIdAndMemberId(buncheolId, memberId))
          .isTrue();
      assertThat(buncheolMemberRepository.existsByBuncheolIdAndMemberId(buncheolId, memberId2))
          .isFalse();
    }
  }

  private Long saveSlot(final Long groupMemberId) {
    BuncheolMember member = BuncheolMember.create(buncheolId, groupMemberId, 10_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    em.flush();
    em.clear();
    return member.getId();
  }

  private Long saveOtherBuncheol() {
    Long otherHostId = TestUserFixture.insertUser(jdbcTemplate, "otherhost");
    Long otherGroupId =
        jdbcTemplate.queryForObject(
            "SELECT group_id FROM buncheols WHERE id = ?", Long.class, buncheolId);
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol other =
        Buncheol.create(
            otherHostId,
            new BuncheolParams(
                otherGroupId, "다른 분철", null, "스토어명", deadline, 1, 3000, null, FlowType.C2C, null),
            Instant.now());
    buncheolRepository.save(other);
    em.flush();
    return other.getId();
  }

  private int participationSeq = 0;

  private void insertParticipationOn(final Long slotId, final String status) {
    participationSeq++;
    Long participantId = TestUserFixture.insertUser(jdbcTemplate, "slotp" + participationSeq);
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, amount,"
            + " shipping_fee, status, flow_type)"
            + " VALUES (?, ?, ?, 0, 0, ?, 'C2C')",
        buncheolId,
        slotId,
        participantId,
        status);
  }

  private boolean slotExists(final Long slotId) {
    return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM buncheol_members WHERE id = ?", Integer.class, slotId)
        > 0;
  }

  private int countMembersByBuncheolId(final Long targetBuncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheol_members WHERE buncheol_id = ?",
        Integer.class,
        targetBuncheolId);
  }
}
