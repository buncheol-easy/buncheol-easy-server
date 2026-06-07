package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse;
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
@DisplayName("계좌이체 결제 happy-path 통합 테스트")
class BuncheolPaymentHappyPathIntegrationTest {

  private static final String HOST_BANK = "국민은행";
  private static final String HOST_ACCOUNT = "12345678";
  private static final String HOST_HOLDER = "홍길동";
  private static final long BID_AMOUNT = 50_000L;
  private static final long SHIPPING_FEE = 3_000L; // GS25 배송비
  private static final long TOTAL_AMOUNT = BID_AMOUNT + SHIPPING_FEE;

  @Autowired private BuncheolService buncheolService;
  @Autowired private BuncheolCheckoutService buncheolCheckoutService;
  @Autowired private HostPaymentService hostPaymentService;
  @Autowired private ParticipationPaymentQueryService participationPaymentQueryService;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long participantId;
  private Long buncheolId;
  private Long participationId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_hp");
    registerHostBankAccount(hostId);
    participantId = insertParticipantWithPhone("participant_hp");

    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "happy-path그룹");
    Long groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "멤버A");

    buncheolId = createBuncheol(hostId, groupId);
    Long buncheolMemberId = createBuncheolMember(buncheolId, groupMemberId);
    Long shippingAddressId = insertShippingAddress(participantId, "GS25 강남점");
    participationId = insertActiveBidParticipation(buncheolMemberId, shippingAddressId);
  }

  @Test
  void 마감부터_입금신고_입금확인_배송스냅샷까지_상태가_끊김없이_흐른다() {
    // 1) 마감 → 멤버 슬롯 1순위 낙찰자가 AWAITING_PAYMENT 로 전이된다.
    buncheolService.closeBuncheol(hostId, buncheolId);
    em.flush();

    assertThat(status()).isEqualTo("AWAITING_PAYMENT");
    assertThat(closedRank()).isEqualTo(1);
    assertThat(dueAt()).isNotNull();

    // 2) 낙찰자 결제 상세 조회 → 금액 계산 + 개최자 계좌 노출.
    ParticipationPaymentDetailResponse awaiting = getDetail();
    assertThat(awaiting.paymentStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
    assertThat(awaiting.bidAmount()).isEqualTo(BID_AMOUNT);
    assertThat(awaiting.shippingFee()).isEqualTo(SHIPPING_FEE);
    assertThat(awaiting.totalAmount()).isEqualTo(TOTAL_AMOUNT);
    assertThat(awaiting.paymentDueAt()).isNotNull();
    assertThat(awaiting.hostAccount()).isNotNull();
    assertThat(awaiting.hostAccount().bankName()).isEqualTo(HOST_BANK);
    assertThat(awaiting.hostAccount().accountNumber()).isEqualTo(HOST_ACCOUNT);
    assertThat(awaiting.hostAccount().accountHolder()).isEqualTo(HOST_HOLDER);

    // 3) 구매자 입금완료 신고 → PAYMENT_REPORTED.
    buncheolCheckoutService.reportPayment(participantId, participationId);
    em.flush();

    assertThat(status()).isEqualTo("PAYMENT_REPORTED");
    assertThat(paymentReportedAt()).isNotNull();

    // 3-1) PAYMENT_REPORTED 단계에서도 개최자 계좌는 계속 노출된다.
    ParticipationPaymentDetailResponse reported = getDetail();
    assertThat(reported.paymentStatus()).isEqualTo(ParticipationStatus.PAYMENT_REPORTED);
    assertThat(reported.hostAccount()).isNotNull();

    // 4) 개최자 입금확인 → CONFIRMED + 배송 스냅샷 생성.
    hostPaymentService.confirmPayment(hostId, participationId);
    em.flush();

    assertThat(status()).isEqualTo("CONFIRMED");
    assertThat(paymentConfirmedAt()).isNotNull();

    // 5) 배송 스냅샷이 participation_id 기준 1건 생성된다.
    Integer deliveryCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deliveries WHERE participation_id = ?",
            Integer.class,
            participationId);
    assertThat(deliveryCount).isEqualTo(1);

    // 6) CONFIRMED 상태에서는 결제 상세 조회 시 개최자 계좌를 더 이상 노출하지 않는다.
    ParticipationPaymentDetailResponse confirmed = getDetail();
    assertThat(confirmed.paymentStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
    assertThat(confirmed.hostAccount()).isNull();
  }

  // --- 상태 조회 헬퍼 ---

  private ParticipationPaymentDetailResponse getDetail() {
    return participationPaymentQueryService.getPaymentDetail(participantId, participationId);
  }

  private String status() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }

  private Integer closedRank() {
    return jdbcTemplate.queryForObject(
        "SELECT closed_rank FROM participations WHERE id = ?", Integer.class, participationId);
  }

  private Timestamp dueAt() {
    return jdbcTemplate.queryForObject(
        "SELECT due_at FROM participations WHERE id = ?", Timestamp.class, participationId);
  }

  private Timestamp paymentReportedAt() {
    return jdbcTemplate.queryForObject(
        "SELECT payment_reported_at FROM participations WHERE id = ?",
        Timestamp.class,
        participationId);
  }

  private Timestamp paymentConfirmedAt() {
    return jdbcTemplate.queryForObject(
        "SELECT payment_confirmed_at FROM participations WHERE id = ?",
        Timestamp.class,
        participationId);
  }

  // --- 시드 헬퍼 ---

  private void registerHostBankAccount(final Long userId) {
    jdbcTemplate.update(
        "UPDATE users SET settlement_bank = ?, settlement_account = ?, settlement_holder = ?"
            + " WHERE id = ?",
        HOST_BANK,
        HOST_ACCOUNT,
        HOST_HOLDER,
        userId);
  }

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
            new BuncheolParams(groupId, "happy-path 분철", null, "스토어", deadline, 3000, null),
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

  private Long insertActiveBidParticipation(
      final Long buncheolMemberId, final Long shippingAddressId) {
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, active_participant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        BID_AMOUNT,
        ParticipationStatus.ACTIVE_BID.name(),
        participantId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE buncheol_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        buncheolId);
  }
}
