package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolManagementOptionResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("미입금 만료·차순위 승계 통합 테스트")
class BuncheolPaymentExpireIntegrationTest {

  private static final long WINNER_BID = 50_000L;
  private static final long RUNNER_UP_BID = 40_000L;

  @Autowired private BuncheolService buncheolService;
  @Autowired private HostPaymentService hostPaymentService;
  @Autowired private BuncheolManagementQueryService buncheolManagementQueryService;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long buncheolId;
  private Long buncheolMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_expire");

    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "만료테스트그룹");
    Long groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "멤버A");

    buncheolId = createBuncheol(hostId, groupId);
    buncheolMemberId = createBuncheolMember(buncheolId, groupMemberId);
  }

  @Test
  void 기한_지난_낙찰자를_만료하면_FAILED_되고_차순위가_입금대기로_승계되어_management_winner로_노출된다() {
    Long winnerId = seedActiveBid("winner_u", WINNER_BID);
    Long runnerUpId = seedActiveBid("runnerup_u", RUNNER_UP_BID);

    // 1) 마감 → 최고가 winner 가 AWAITING_PAYMENT(closedRank=1), 차순위는 ACTIVE_BID(closedRank=2) 로 남는다.
    buncheolService.closeBuncheol(hostId, buncheolId);
    em.flush();
    assertThat(status(winnerId)).isEqualTo("AWAITING_PAYMENT");
    assertThat(status(runnerUpId)).isEqualTo("ACTIVE_BID");
    assertThat(closedRank(runnerUpId)).isEqualTo(2);

    // 2) winner 의 입금 기한을 과거로 돌려 만료 대상으로 만든다.
    expireDueAt(winnerId);
    em.flush();
    em.clear();

    // 3) 개최자가 만료 처리 → winner FAILED, 차순위가 AWAITING_PAYMENT 로 승계된다.
    //    승계가 성공한다는 사실 자체가, 가드(existsPaymentInProgressInSlot)가 직전에 FAILED 로 바뀐
    //    winner 를 (auto-flush 로) 제외하고 조회했음을 증명한다.
    hostPaymentService.expirePayment(hostId, winnerId);
    em.flush();

    assertThat(status(winnerId)).isEqualTo("FAILED");
    assertThat(status(runnerUpId)).isEqualTo("AWAITING_PAYMENT");
    assertThat(closedRank(runnerUpId)).isEqualTo(2); // 마감 순위 보존
    assertThat(dueAt(runnerUpId)).isNotNull();
    assertThat(dueAt(runnerUpId).toInstant()).isAfter(Instant.now()); // 새 입금 기한(미래)

    // 4) management winner 가 승계된 차순위(입금대기)로 노출된다.
    BuncheolManagementOptionResponse option = managementOption();
    assertThat(option.winner()).isNotNull();
    assertThat(option.winner().participationId()).isEqualTo(runnerUpId);
    assertThat(option.winner().paymentStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
    assertThat(option.winner().bidAmount()).isEqualTo(RUNNER_UP_BID);
  }

  @Test
  void 차순위_후보가_없으면_낙찰자만_FAILED_되고_management_winner_는_사라진다() {
    Long winnerId = seedActiveBid("solo_winner_u", WINNER_BID);

    buncheolService.closeBuncheol(hostId, buncheolId);
    em.flush();
    assertThat(status(winnerId)).isEqualTo("AWAITING_PAYMENT");

    expireDueAt(winnerId);
    em.flush();
    em.clear();

    hostPaymentService.expirePayment(hostId, winnerId);
    em.flush();

    assertThat(status(winnerId)).isEqualTo("FAILED");
    assertThat(managementOption().winner()).isNull();
  }

  // --- 조회 헬퍼 ---

  private BuncheolManagementOptionResponse managementOption() {
    BuncheolManagementResponse response =
        buncheolManagementQueryService.getManagement(buncheolId, hostId);
    return response.options().stream()
        .filter(o -> o.buncheolMemberId().equals(buncheolMemberId))
        .findFirst()
        .orElseThrow();
  }

  private String status(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }

  private Integer closedRank(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT closed_rank FROM participations WHERE id = ?", Integer.class, participationId);
  }

  private Timestamp dueAt(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT due_at FROM participations WHERE id = ?", Timestamp.class, participationId);
  }

  // --- 시드 헬퍼 ---

  private void expireDueAt(final Long participationId) {
    jdbcTemplate.update(
        "UPDATE participations SET due_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        participationId);
  }

  private Long createBuncheol(final Long hostId, final Long groupId) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "만료 테스트 분철", null, "스토어", deadline, 3000, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    return buncheol.getId();
  }

  private Long createBuncheolMember(final Long buncheolId, final Long groupMemberId) {
    BuncheolMember member = BuncheolMember.create(buncheolId, groupMemberId, 30_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    em.flush();
    return member.getId();
  }

  private Long seedActiveBid(final String providerId, final long bidAmount) {
    Long participantId = TestUserFixture.insertUser(jdbcTemplate, providerId);
    Long shippingAddressId = insertShippingAddress(participantId, providerId + "_매장");
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, active_participant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        bidAmount,
        ParticipationStatus.ACTIVE_BID.name(),
        participantId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE participant_id = ? AND buncheol_member_id = ?",
        Long.class,
        participantId,
        buncheolMemberId);
  }

  private Long insertShippingAddress(final Long userId, final String storeName) {
    jdbcTemplate.update(
        "INSERT INTO shipping_addresses (user_id, shipping_method, store_name) VALUES (?, ?, ?)",
        userId,
        "GS25_HALF",
        storeName);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM shipping_addresses WHERE user_id = ? AND store_name = ?",
        Long.class,
        userId,
        storeName);
  }
}
