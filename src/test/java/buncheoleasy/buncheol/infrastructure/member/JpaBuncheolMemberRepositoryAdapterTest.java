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
import jakarta.persistence.LockModeType;
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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaBuncheolMemberRepositoryAdapter 테스트")
class JpaBuncheolMemberRepositoryAdapterTest {

  @Autowired private BuncheolMemberRepository buncheolMemberRepository;

  // 어댑터를 거치지 않고 FK 위반 메시지를 직접 보려고 쓴다(같은 패키지의 package-private 인터페이스).
  @Autowired private JpaBuncheolMemberRepository jpaBuncheolMemberRepository;

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

    // 어댑터의 FK 판별(제약 이름)이 H2 의 Hibernate 번역 메시지에서도 맞는지 — 조건 없는 JPQL 삭제로 FK 를 직접 일으킨다.
    @Test
    void H2_에서_참여_FK_위반_메시지에_FK_이름이_실린다() {
      Long participatedSlot = saveSlot(memberId);
      insertParticipationOn(participatedSlot, "CANCELLED");

      assertThatThrownBy(() -> jpaBuncheolMemberRepository.deleteAllByBuncheolId(buncheolId))
          .isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(
              ex ->
                  assertThat(ex.getMessage().toLowerCase())
                      .contains("fk_participations_buncheol_member"));
    }

    @Test
    void H2_에서_참여_코드_FK_위반_메시지에_FK_이름이_실린다() {
      Long codedSlot = saveSlot(memberId);
      jdbcTemplate.update(
          "INSERT INTO participation_codes (code, buncheol_id, buncheol_member_id, expires_at)"
              + " VALUES ('CODE6789', ?, ?, ?)",
          buncheolId,
          codedSlot,
          Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));

      assertThatThrownBy(() -> jpaBuncheolMemberRepository.deleteAllByBuncheolId(buncheolId))
          .isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(
              ex ->
                  assertThat(ex.getMessage().toLowerCase())
                      .contains("fk_participation_codes_member"));
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
    void 분철_안_멤버의_자리를_찾는다() {
      Long slotId = saveSlot(memberId);

      assertThat(buncheolMemberRepository.findByBuncheolIdAndMemberId(buncheolId, memberId))
          .map(BuncheolMember::getId)
          .contains(slotId);
      assertThat(buncheolMemberRepository.findByBuncheolIdAndMemberId(buncheolId, memberId2))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("빈 자리 가격 바꾸기 CAS 테스트")
  class ChangePriceIfUnoccupiedTest {

    private long priceColumn(final Long slotId) {
      return jdbcTemplate.queryForObject(
          "SELECT price FROM buncheol_members WHERE id = ?", Long.class, slotId);
    }

    @Test
    void 신청자가_없는_선착순_자리는_가격이_바뀐다() {
      Long slotId = saveSlot(memberId);

      assertThat(buncheolMemberRepository.changePriceIfUnoccupied(slotId, buncheolId, 15_000L))
          .isTrue();

      assertThat(priceColumn(slotId)).isEqualTo(15_000L);
    }

    // 이 테스트가 가드 본체다 — NOT EXISTS 를 지우면 신청자가 본 가격이 바뀌어 빨개진다.
    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "AWAITING_PAYMENT", "PAYMENT_SENT", "CONFIRMED"})
    void 활성_참여가_있는_자리는_바꾸지_않는다(final String status) {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, status);

      assertThat(buncheolMemberRepository.changePriceIfUnoccupied(slotId, buncheolId, 15_000L))
          .isFalse();
      assertThat(priceColumn(slotId)).isEqualTo(10_000L);
    }

    @Test
    void 취소_기록만_있는_자리는_바뀐다() {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, "CANCELLED");

      assertThat(buncheolMemberRepository.changePriceIfUnoccupied(slotId, buncheolId, 15_000L))
          .isTrue();
    }

    // 닫힌 자리는 다시 넣기(가격 포함)로만 연다 — 가격만 바꿔 두면 다시 열 때의 가격과 갈린다.
    @Test
    void 닫힌_자리는_바꾸지_않는다() {
      Long slotId = saveSlot(memberId);
      jdbcTemplate.update(
          "UPDATE buncheol_members SET access_type = 'CLOSED' WHERE id = ?", slotId);

      assertThat(buncheolMemberRepository.changePriceIfUnoccupied(slotId, buncheolId, 15_000L))
          .isFalse();
      assertThat(priceColumn(slotId)).isEqualTo(10_000L);
    }

    @Test
    void 다른_분철의_자리는_바꾸지_않는다() {
      Long slotId = saveSlot(memberId);

      assertThat(
              buncheolMemberRepository.changePriceIfUnoccupied(
                  slotId, buncheolId + 9_999L, 15_000L))
          .isFalse();
      assertThat(priceColumn(slotId)).isEqualTo(10_000L);
    }

    // 접근 정책·다른 칸은 그대로 둔다 — 행 전체를 다시 쓰는 더티체킹(updatePrice)을 쓰지 않는 이유다.
    @Test
    void 가격만_바꾸고_접근_정책은_그대로_둔다() {
      Long slotId = saveSlot(memberId);

      buncheolMemberRepository.changePriceIfUnoccupied(slotId, buncheolId, 15_000L);

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT access_type FROM buncheol_members WHERE id = ?", String.class, slotId))
          .isEqualTo("OPEN");
    }
  }

  @Nested
  @DisplayName("자리 닫기·다시 열기 테스트")
  class CloseAndReopenTest {

    private String accessTypeColumn(final Long slotId) {
      return jdbcTemplate.queryForObject(
          "SELECT access_type FROM buncheol_members WHERE id = ?", String.class, slotId);
    }

    private long priceColumn(final Long slotId) {
      return jdbcTemplate.queryForObject(
          "SELECT price FROM buncheol_members WHERE id = ?", Long.class, slotId);
    }

    // 닫기는 기존 접근 정책 CAS 를 그대로 쓴다 — 취소 기록만 있으면 활성 참여가 아니라 닫힌다.
    @Test
    void 취소_기록만_있는_자리는_CLOSED_로_닫히고_엔티티로_다시_읽힌다() {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, "CANCELLED");

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, BuncheolMemberAccessType.CLOSED))
          .isTrue();

      em.clear();
      assertThat(accessTypeColumn(slotId)).isEqualTo("CLOSED");
      // @Enumerated(STRING) 왕복 — 이 값을 모르는 서버는 여기서 500 이 난다(롤백 주의).
      BuncheolMember loaded =
          buncheolMemberRepository.findByIdAndBuncheolId(slotId, buncheolId).orElseThrow();
      assertThat(loaded.isClosed()).isTrue();
      assertThat(loaded.requiresCode()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "AWAITING_PAYMENT", "PAYMENT_SENT", "CONFIRMED"})
    void 활성_참여가_있는_자리는_닫히지_않는다(final String status) {
      Long slotId = saveSlot(memberId);
      insertParticipationOn(slotId, status);

      assertThat(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  slotId, buncheolId, BuncheolMemberAccessType.CLOSED))
          .isFalse();
      assertThat(accessTypeColumn(slotId)).isEqualTo("OPEN");
    }

    @Test
    void 닫힌_자리는_선착순으로_다시_열리고_가격이_바뀐다() {
      Long slotId = saveSlot(memberId);
      jdbcTemplate.update(
          "UPDATE buncheol_members SET access_type = 'CLOSED' WHERE id = ?", slotId);

      assertThat(buncheolMemberRepository.reopenIfClosed(slotId, buncheolId, 15_000L)).isTrue();

      assertThat(accessTypeColumn(slotId)).isEqualTo("OPEN");
      assertThat(priceColumn(slotId)).isEqualTo(15_000L);
    }

    // 열린 자리를 「다시 열기」로 가격만 바꾸는 우회로가 되면 안 된다 — 닫힌 자리만 대상이다.
    @Test
    void 열린_자리는_다시_열기로_바뀌지_않는다() {
      Long slotId = saveSlot(memberId);

      assertThat(buncheolMemberRepository.reopenIfClosed(slotId, buncheolId, 15_000L)).isFalse();

      assertThat(priceColumn(slotId)).isEqualTo(10_000L);
    }

    @Test
    void 다른_분철의_자리는_다시_열지_않는다() {
      Long slotId = saveSlot(memberId);
      jdbcTemplate.update(
          "UPDATE buncheol_members SET access_type = 'CLOSED' WHERE id = ?", slotId);

      assertThat(buncheolMemberRepository.reopenIfClosed(slotId, buncheolId + 9_999L, 15_000L))
          .isFalse();
      assertThat(accessTypeColumn(slotId)).isEqualTo("CLOSED");
    }
  }

  @Nested
  @DisplayName("신청 경로 자리 잠금 읽기 테스트")
  class FindByIdAndBuncheolIdForShareTest {

    // 신청 트랜잭션의 스냅샷은 분철 락 전에 잡혀, 일반 조회로는 그 사이 닫힌 자리를 「열림」으로 본다 — 잠금 읽기여야 한다.
    @Test
    void 공유_잠금_읽기로_선언돼_있다() throws NoSuchMethodException {
      Lock lock =
          JpaBuncheolMemberRepository.class
              .getMethod("findByIdAndBuncheolIdForShare", Long.class, Long.class)
              .getAnnotation(Lock.class);

      assertThat(lock).isNotNull();
      assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_READ);
    }

    // 정원 판정의 분모도 신청 트랜잭션 스냅샷이 아니라 최신 커밋으로 세야 한다.
    @Test
    void 정원_판정용_자리_목록도_공유_잠금_읽기로_선언돼_있다() throws NoSuchMethodException {
      Lock lock =
          JpaBuncheolMemberRepository.class
              .getMethod("findAllByBuncheolIdForShare", Long.class)
              .getAnnotation(Lock.class);

      assertThat(lock).isNotNull();
      assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_READ);
    }

    @Test
    void 정원_판정용_자리_목록은_닫힌_자리까지_그_분철_것만_돌려준다() {
      Long openSlot = saveSlot(memberId);
      Long closedSlot = saveSlot(memberId2);
      jdbcTemplate.update(
          "UPDATE buncheol_members SET access_type = 'CLOSED' WHERE id = ?", closedSlot);
      Long otherBuncheolId = saveOtherBuncheol();
      buncheolMemberRepository.saveAll(
          List.of(BuncheolMember.create(otherBuncheolId, memberId3, 1_000L)));
      em.flush();
      em.clear();

      List<BuncheolMember> slots = buncheolMemberRepository.findAllByBuncheolIdForShare(buncheolId);

      assertThat(slots)
          .extracting(BuncheolMember::getId)
          .containsExactlyInAnyOrder(openSlot, closedSlot);
      assertThat(slots.stream().filter(slot -> !slot.isClosed()).count()).isEqualTo(1);
    }

    @Test
    void 그_분철의_자리만_돌려준다() {
      Long slotId = saveSlot(memberId);

      assertThat(buncheolMemberRepository.findByIdAndBuncheolIdForShare(slotId, buncheolId))
          .map(BuncheolMember::getId)
          .contains(slotId);
      assertThat(
              buncheolMemberRepository.findByIdAndBuncheolIdForShare(slotId, buncheolId + 9_999L))
          .isEmpty();
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
