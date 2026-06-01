package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 마감(CAS close) + 낙찰 선정(dirty checking) 이 같은 트랜잭션에서 실제 DB 에 올바르게 영속되는지 검증하는 end-to-end 통합 테스트.
 * 단위 테스트(mock)로는 잡히지 않는 flush/CAS 상호작용을 실 H2 로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("분철 자동 마감 + 낙찰 선정 통합 테스트")
class BuncheolAutoCloseServiceIntegrationTest {

  @Autowired private BuncheolAutoCloseService buncheolAutoCloseService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long groupId;
  private Long groupMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_close");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "마감테스트그룹");
    groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "마감멤버");
  }

  @Test
  void 마감_시_멤버별_최고가는_낙찰_나머지는_탈락_처리되고_분철은_CLOSED_된다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "RECRUITING");
    Long buncheolMemberId = insertBuncheolMember(buncheolId);
    Long winnerId = TestUserFixture.insertUser(jdbcTemplate, "winner_close");
    Long loserId = TestUserFixture.insertUser(jdbcTemplate, "loser_close");
    Long winnerParticipationId =
        insertActiveBid(buncheolId, buncheolMemberId, winnerId, 50_000L);
    Long loserParticipationId = insertActiveBid(buncheolId, buncheolMemberId, loserId, 30_000L);

    boolean closed = buncheolAutoCloseService.closeExpired(buncheolId, Instant.now());
    em.flush();

    assertThat(closed).isTrue();
    assertThat(buncheolStatus(buncheolId)).isEqualTo("CLOSED");
    assertThat(closedAt(buncheolId)).isNotNull();

    assertThat(participationStatus(winnerParticipationId)).isEqualTo("AWAITING_PAYMENT");
    assertThat(closedRank(winnerParticipationId)).isEqualTo(1);

    assertThat(participationStatus(loserParticipationId)).isEqualTo("FAILED");
    assertThat(closedRank(loserParticipationId)).isEqualTo(2);
  }

  @Test
  void 이미_마감된_분철에_다시_시도하면_false를_반환한다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "CLOSED");

    boolean closed = buncheolAutoCloseService.closeExpired(buncheolId, Instant.now());

    assertThat(closed).isFalse();
  }

  @Test
  void deadline이_지난_RECRUITING_분철만_만료_대상으로_조회된다() {
    Long expired = insertBuncheol(pastDeadline(), "RECRUITING");
    Long notYet = insertBuncheol(futureDeadline(), "RECRUITING");
    Long alreadyClosed = insertBuncheol(pastDeadline(), "CLOSED");

    List<Long> result = buncheolAutoCloseService.findExpiredBuncheolIds(Instant.now());

    assertThat(result).contains(expired).doesNotContain(notYet, alreadyClosed);
  }

  @Test
  void 동점이면_먼저_입찰한_참여가_낙찰된다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "RECRUITING");
    Long buncheolMemberId = insertBuncheolMember(buncheolId);
    Long earlyBidderId = TestUserFixture.insertUser(jdbcTemplate, "tie_early");
    Long lateBidderId = TestUserFixture.insertUser(jdbcTemplate, "tie_late");
    // 같은 제시가(동점) — 먼저 입찰(낮은 id)한 쪽이 낙찰돼야 한다 (정렬 bidAmount DESC, id ASC).
    Long earlyParticipationId =
        insertActiveBid(buncheolId, buncheolMemberId, earlyBidderId, 40_000L);
    Long lateParticipationId =
        insertActiveBid(buncheolId, buncheolMemberId, lateBidderId, 40_000L);

    buncheolAutoCloseService.closeExpired(buncheolId, Instant.now());
    em.flush();

    assertThat(participationStatus(earlyParticipationId)).isEqualTo("AWAITING_PAYMENT");
    assertThat(closedRank(earlyParticipationId)).isEqualTo(1);
    assertThat(participationStatus(lateParticipationId)).isEqualTo("FAILED");
    assertThat(closedRank(lateParticipationId)).isEqualTo(2);
  }

  // --- fixtures ---

  private Instant pastDeadline() {
    return Instant.now().minus(1, ChronoUnit.HOURS);
  }

  private Instant futureDeadline() {
    return Instant.now().plus(7, ChronoUnit.DAYS);
  }

  private Long insertBuncheol(final Instant deadline, final String status) {
    jdbcTemplate.update(
        "INSERT INTO buncheols (host_id, group_id, title, purchase_site, deadline,"
            + " gs25_shipping_fee, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
        hostId,
        groupId,
        "마감 테스트 분철",
        "store",
        Timestamp.from(deadline),
        3000,
        status);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM buncheols WHERE host_id = ? ORDER BY id DESC LIMIT 1", Long.class, hostId);
  }

  private Long insertBuncheolMember(final Long buncheolId) {
    jdbcTemplate.update(
        "INSERT INTO buncheol_members (buncheol_id, member_id, bid_min_price) VALUES (?, ?, ?)",
        buncheolId,
        groupMemberId,
        10_000L);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM buncheol_members WHERE buncheol_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        buncheolId);
  }

  private Long insertActiveBid(
      final Long buncheolId, final Long buncheolMemberId, final Long participantId, final long bidAmount) {
    Long shippingAddressId = insertShippingAddress(participantId);
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, active_participant_id)"
            + " VALUES (?, ?, ?, ?, ?, 'ACTIVE_BID', ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        bidAmount,
        participantId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE buncheol_id = ? AND participant_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        buncheolId,
        participantId);
  }

  private Long insertShippingAddress(final Long userId) {
    jdbcTemplate.update(
        "INSERT INTO shipping_addresses (user_id, shipping_method, store_name) VALUES (?, ?, ?)",
        userId,
        "GS25_HALF",
        "GS25 지점");
    return jdbcTemplate.queryForObject(
        "SELECT id FROM shipping_addresses WHERE user_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        userId);
  }

  private String buncheolStatus(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private Timestamp closedAt(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT closed_at FROM buncheols WHERE id = ?", Timestamp.class, buncheolId);
  }

  private String participationStatus(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }

  private Integer closedRank(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT closed_rank FROM participations WHERE id = ?", Integer.class, participationId);
  }
}
