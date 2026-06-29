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
 * 분철 마감 판정(CAS finalize) + 활성 참여 일괄 취소 + 배송 스냅샷 생성이 같은 트랜잭션에서 실제 DB 에 올바르게 영속되는지 검증하는 end-to-end
 * 통합 테스트. 단위 테스트(mock)로는 잡히지 않는 flush/CAS 상호작용을 실 H2 로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("분철 자동 마감 판정 통합 테스트")
class BuncheolAutoCloseServiceIntegrationTest {

  @Autowired private BuncheolAutoCloseService buncheolAutoCloseService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long groupId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_close");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "마감테스트그룹");
  }

  @Test
  void 확정_참여가_최소_인원_이상이면_진행확정되고_입금확인중은_그대로_남으며_배송_스냅샷은_finalize에서_생성하지_않는다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "RECRUITING", 1);
    Long confirmedSlot = insertBuncheolMember(buncheolId, "확정멤버");
    Long awaitingSlot = insertBuncheolMember(buncheolId, "대기멤버");
    Long confirmedId =
        insertParticipation(buncheolId, confirmedSlot, "fan_confirmed", "CONFIRMED");
    Long awaitingId =
        insertParticipation(buncheolId, awaitingSlot, "fan_awaiting", "AWAITING_PAYMENT");

    boolean finalized = buncheolAutoCloseService.finalizeExpired(buncheolId, Instant.now());
    em.flush();

    assertThat(finalized).isTrue();
    assertThat(buncheolStatus(buncheolId)).isEqualTo("CONFIRMED");
    assertThat(finalizedAt(buncheolId)).isNotNull();

    // 확정 참여는 유지. 남은 입금확인중 참여는 finalize 에서 건드리지 않고 입금 만료 스케줄러에 위임한다(자동취소 알림 중복 방지).
    assertThat(participationStatus(confirmedId)).isEqualTo("CONFIRMED");
    assertThat(participationStatus(awaitingId)).isEqualTo("AWAITING_PAYMENT");

    // 배송 스냅샷은 진행확정(finalize)이 아니라 개별 참여의 입금확인 시점에 생성되므로, finalize 는 스냅샷을 만들지 않는다.
    assertThat(deliveryCountFor(confirmedId)).isZero();
    assertThat(deliveryCountFor(awaitingId)).isZero();
  }

  @Test
  void 확정_참여가_최소_인원_미만이면_분철과_활성_참여가_모두_취소된다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "RECRUITING", 2);
    Long confirmedSlot = insertBuncheolMember(buncheolId, "확정멤버");
    Long awaitingSlot = insertBuncheolMember(buncheolId, "대기멤버");
    Long confirmedId =
        insertParticipation(buncheolId, confirmedSlot, "fan_confirmed", "CONFIRMED");
    Long awaitingId =
        insertParticipation(buncheolId, awaitingSlot, "fan_awaiting", "AWAITING_PAYMENT");

    boolean finalized = buncheolAutoCloseService.finalizeExpired(buncheolId, Instant.now());
    em.flush();

    assertThat(finalized).isTrue();
    assertThat(buncheolStatus(buncheolId)).isEqualTo("CANCELLED");

    // 입금확인중·확정 모두 BUNCHEOL_CANCELLED 로 취소. 확정 건은 운영자 오프라인 환불 대상.
    assertThat(participationStatus(confirmedId)).isEqualTo("CANCELLED");
    assertThat(participationStatus(awaitingId)).isEqualTo("CANCELLED");
    assertThat(cancelReason(confirmedId)).isEqualTo("BUNCHEOL_CANCELLED");
    assertThat(cancelReason(awaitingId)).isEqualTo("BUNCHEOL_CANCELLED");

    // 취소 시 배송 스냅샷은 생성되지 않는다.
    assertThat(deliveryCountFor(confirmedId)).isZero();
  }

  @Test
  void 이미_마감된_분철에_다시_시도하면_false를_반환한다() {
    Long buncheolId = insertBuncheol(pastDeadline(), "CONFIRMED", 1);

    boolean finalized = buncheolAutoCloseService.finalizeExpired(buncheolId, Instant.now());

    assertThat(finalized).isFalse();
  }

  @Test
  void deadline이_지난_RECRUITING_분철만_만료_대상으로_조회된다() {
    Long expired = insertBuncheol(pastDeadline(), "RECRUITING", 1);
    Long notYet = insertBuncheol(futureDeadline(), "RECRUITING", 1);
    Long alreadyFinalized = insertBuncheol(pastDeadline(), "CONFIRMED", 1);

    List<Long> result = buncheolAutoCloseService.findExpiredBuncheolIds(Instant.now());

    assertThat(result).contains(expired).doesNotContain(notYet, alreadyFinalized);
  }

  // --- fixtures ---

  private Instant pastDeadline() {
    return Instant.now().minus(1, ChronoUnit.HOURS);
  }

  private Instant futureDeadline() {
    return Instant.now().plus(7, ChronoUnit.DAYS);
  }

  private Long insertBuncheol(final Instant deadline, final String status, final int minHeadcount) {
    jdbcTemplate.update(
        "INSERT INTO buncheols (host_id, group_id, title, purchase_site, deadline,"
            + " min_headcount, gs25_shipping_fee, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        hostId,
        groupId,
        "마감 테스트 분철",
        "store",
        Timestamp.from(deadline),
        minHeadcount,
        3000,
        status);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM buncheols WHERE host_id = ? ORDER BY id DESC LIMIT 1", Long.class, hostId);
  }

  private Long insertBuncheolMember(final Long buncheolId, final String memberName) {
    Long memberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, memberName);
    jdbcTemplate.update(
        "INSERT INTO buncheol_members (buncheol_id, member_id, price) VALUES (?, ?, ?)",
        buncheolId,
        memberId,
        30_000L);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM buncheol_members WHERE buncheol_id = ? AND member_id = ?",
        Long.class,
        buncheolId,
        memberId);
  }

  private Long insertParticipation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final String userSuffix,
      final String status) {
    Long participantId = insertUserWithPhone(userSuffix);
    Long shippingAddressId = insertShippingAddress(participantId);
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, amount, refund_bank, refund_account, refund_holder,"
            + " due_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        30_000L,
        "국민",
        "12345678",
        "홍길동",
        Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)),
        status);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE participant_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        participantId);
  }

  // 배송 스냅샷 생성 시 user.getPhoneNumber().value() 가 필요하므로 전화번호를 채워 넣는다.
  private Long insertUserWithPhone(final String providerId) {
    String nickname = "Guest" + providerId.replaceAll("[^a-zA-Z0-9]", "");
    jdbcTemplate.update(
        "INSERT INTO users (provider, provider_id, email, nickname, phone_number, profile_completed)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        "KAKAO",
        providerId,
        providerId + "@example.com",
        nickname,
        "01012345678",
        true);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE provider_id = ?", Long.class, providerId);
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

  private Timestamp finalizedAt(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT finalized_at FROM buncheols WHERE id = ?", Timestamp.class, buncheolId);
  }

  private String participationStatus(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }

  private String cancelReason(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT cancel_reason FROM participations WHERE id = ?", String.class, participationId);
  }

  private int deliveryCountFor(final Long participationId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deliveries WHERE participation_id = ?",
            Integer.class,
            participationId);
    return count == null ? 0 : count;
  }
}
