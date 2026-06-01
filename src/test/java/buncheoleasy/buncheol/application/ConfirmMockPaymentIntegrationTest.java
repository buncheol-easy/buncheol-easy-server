package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("분철 낙찰자 mock 결제 확정 통합 테스트")
class ConfirmMockPaymentIntegrationTest {

  @Autowired private BuncheolCheckoutService buncheolCheckoutService;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long participantId;
  private Long buncheolId;
  private Long buncheolMemberId;
  private Long shippingAddressId;

  @BeforeEach
  void setUp() {
    Long hostId = TestUserFixture.insertUser(jdbcTemplate, "host_pay");
    participantId = insertParticipantWithPhone("participant_pay");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "결제테스트그룹");
    Long groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "결제멤버");

    buncheolId = createBuncheol(hostId, groupId);
    buncheolMemberId = createBuncheolMember(buncheolId, groupMemberId);
    shippingAddressId = insertShippingAddress(participantId, "GS25 강남점");
  }

  @Test
  void 낙찰자가_호출하면_CONFIRMED로_전환되고_배송_스냅샷이_생성된다() {
    Long participationId = insertParticipation(ParticipationStatus.AWAITING_PAYMENT, 50_000L);

    buncheolCheckoutService.confirmMockPayment(participantId, participationId);
    em.flush();

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM participations WHERE id = ?", String.class, participationId);
    assertThat(status).isEqualTo("CONFIRMED");

    Integer deliveryCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deliveries WHERE participation_id = ?",
            Integer.class,
            participationId);
    assertThat(deliveryCount).isEqualTo(1);

    // 결제 수단 미확정 mock 이라도 완료(DONE) 결제가 제시가+배송비 금액으로 1건 기록된다.
    Integer paymentCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE participation_id = ? AND status = 'DONE'",
            Integer.class,
            participationId);
    assertThat(paymentCount).isEqualTo(1);

    Long paidAmount =
        jdbcTemplate.queryForObject(
            "SELECT amount FROM payments WHERE participation_id = ? AND status = 'DONE'",
            Long.class,
            participationId);
    assertThat(paidAmount).isEqualTo(53_000L); // 제시가 50,000 + GS25 배송비 3,000
  }

  @Test
  void 이미_확정된_참여를_다시_결제하면_예외이고_결제는_1건만_남는다() {
    Long participationId = insertParticipation(ParticipationStatus.AWAITING_PAYMENT, 50_000L);
    buncheolCheckoutService.confirmMockPayment(participantId, participationId);
    em.flush();
    em.clear();

    // 두 번째 확정은 AWAITING_PAYMENT 가드에 막혀 예외 → 결제도 추가로 기록되지 않는다.
    assertThatThrownBy(
            () -> buncheolCheckoutService.confirmMockPayment(participantId, participationId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);

    Integer paymentCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE participation_id = ?",
            Integer.class,
            participationId);
    assertThat(paymentCount).isEqualTo(1);
  }

  @Test
  void 소유자가_아니면_결제_권한_예외가_발생한다() {
    Long participationId = insertParticipation(ParticipationStatus.AWAITING_PAYMENT, 50_000L);
    Long otherUserId = TestUserFixture.insertUser(jdbcTemplate, "other_pay");

    assertThatThrownBy(
            () -> buncheolCheckoutService.confirmMockPayment(otherUserId, participationId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYMENT_NO_PERMISSION);
  }

  @Test
  void AWAITING_PAYMENT가_아니면_상태_전이_예외가_발생한다() {
    Long participationId = insertParticipation(ParticipationStatus.ACTIVE_BID, 50_000L);

    assertThatThrownBy(
            () -> buncheolCheckoutService.confirmMockPayment(participantId, participationId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
  }

  // --- fixtures ---

  private Long insertParticipantWithPhone(final String providerId) {
    jdbcTemplate.update(
        "INSERT INTO users (provider, provider_id, email, nickname, phone_number, profile_completed)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        "KAKAO",
        providerId,
        providerId + "@example.com",
        "구매자",
        "01012345678",
        true);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE provider_id = ?", Long.class, providerId);
  }

  private Long createBuncheol(final Long hostId, final Long groupId) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "결제 테스트 분철", null, "스토어", deadline, 3000, null),
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

  private Long insertParticipation(final ParticipationStatus status, final long bidAmount) {
    Long activeParticipantId =
        switch (status) {
          case ACTIVE_BID, AWAITING_PAYMENT, CONFIRMED -> participantId;
          default -> null;
        };
    Integer closedRank = status == ParticipationStatus.AWAITING_PAYMENT ? 1 : null;
    Timestamp dueAt =
        status == ParticipationStatus.AWAITING_PAYMENT
            ? Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS))
            : null;
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, closed_rank, due_at, active_participant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        bidAmount,
        status.name(),
        closedRank,
        dueAt,
        activeParticipantId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE buncheol_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        buncheolId);
  }
}
