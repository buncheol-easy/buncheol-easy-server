package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 분철 저장소 쿼리를 실제 MySQL 문법·서브쿼리 규칙(error 1093)에서 돌린다. */
@DisplayName("분철 저장소 쿼리 (MySQL)")
class BuncheolRepositoryMySqlTest extends MySqlContainerTestSupport {

  private static final Duration HOUR = Duration.ofHours(1);

  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private ParticipationRepository participationRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private MySqlFixture fixture;
  private long hostId;
  private long groupId;

  @BeforeEach
  void setUp() {
    fixture = new MySqlFixture(jdbcTemplate);
    hostId = fixture.user(false);
    groupId = fixture.group();
  }

  private long buncheol(final String flowType, final String status, final Duration deadline) {
    return fixture.buncheol(hostId, groupId, flowType, status, deadline, 1, null);
  }

  @Nested
  @DisplayName("마감 폴링 findRecruitingIdsPastDeadline")
  class DeadlinePolling {

    // 운영진 분철 마감 판정이 이 쿼리를 함께 쓴다 — 회원 개최 분기를 바꿔도 운영진 마감 분철은 그대로 뽑혀야 한다.
    @Test
    void 마감이_지난_모집_중_분철을_운영진과_회원_개최_모두_마감_순으로_뽑는다() {
      long staffExpired = buncheol("LEGACY", "RECRUITING", HOUR.multipliedBy(-2));
      long memberExpired = buncheol("C2C", "RECRUITING", HOUR.negated());
      buncheol("LEGACY", "RECRUITING", HOUR);
      buncheol("C2C", "RECRUITING", HOUR);

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100))
          .containsExactly(staffExpired, memberExpired);
    }

    // 옛 규칙은 회원 개최를 마감 + 48시간까지 기다렸다. 이제 마감 즉시 입금 단계 시작이나 미달 취소로 간다.
    @Test
    void 회원_개최는_마감이_막_지나도_뽑는다() {
      long justExpired = buncheol("C2C", "RECRUITING", Duration.ofMinutes(-1));

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100))
          .containsExactly(justExpired);
    }

    @Test
    void 모집_중이_아닌_분철은_마감이_지나도_뽑지_않는다() {
      buncheol("C2C", "PAYMENT_COLLECTING", HOUR.negated());
      buncheol("LEGACY", "CONFIRMED", HOUR.negated());
      buncheol("C2C", "CANCELLED", HOUR.negated());

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100)).isEmpty();
    }

    @Test
    void 배치_상한까지만_뽑는다() {
      long first = buncheol("C2C", "RECRUITING", HOUR.multipliedBy(-3));
      long second = buncheol("LEGACY", "RECRUITING", HOUR.multipliedBy(-2));
      buncheol("C2C", "RECRUITING", HOUR.negated());

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 2))
          .containsExactly(first, second);
    }
  }

  @Nested
  @DisplayName("진행 확정 CAS confirmIfCollecting")
  class ConfirmIfCollecting {

    @Test
    void 회원_개최_입금_수집_중이면_진행_확정으로_넘기고_입금_단계_시작_시각은_남긴다() {
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", HOUR.negated(), 1, HOUR.negated());
      Object finalizedBefore = finalizedAt(buncheolId);

      int updated =
          transactionTemplate.execute(
              status -> buncheolRepository.confirmIfCollecting(buncheolId, Instant.now()));

      assertThat(updated).isEqualTo(1);
      assertThat(status(buncheolId)).isEqualTo("CONFIRMED");
      assertThat(finalizedAt(buncheolId)).isEqualTo(finalizedBefore);
    }

    @Test
    void 입금_수집_중이_아니거나_운영진_분철이면_넘기지_않는다() {
      long recruiting = buncheol("C2C", "RECRUITING", HOUR);
      long staff = fixture.buncheol(hostId, groupId, "LEGACY", "PAYMENT_COLLECTING", HOUR, 1, HOUR);

      for (long buncheolId : new long[] {recruiting, staff}) {
        Integer updated =
            transactionTemplate.execute(
                status -> buncheolRepository.confirmIfCollecting(buncheolId, Instant.now()));
        assertThat(updated).isZero();
      }
      assertThat(status(recruiting)).isEqualTo("RECRUITING");
      assertThat(status(staff)).isEqualTo("PAYMENT_COLLECTING");
    }
  }

  @Nested
  @DisplayName("입금 미확인 참여 취소 cancelUnconfirmedByBuncheolId")
  class CancelUnconfirmed {

    @Test
    void 그_분철의_입금_대기와_보냈어요만_입금_미확인_사유로_취소한다() {
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", HOUR.negated(), 1, HOUR.negated());
      long otherBuncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", HOUR.negated(), 1, HOUR.negated());
      long participantId = fixture.user(false);
      long addressId = fixture.shippingAddress(participantId);
      long bundleId = fixture.bundle(buncheolId, participantId, addressId, HOUR.negated());
      long awaiting = slotParticipation(buncheolId, participantId, bundleId, "AWAITING_PAYMENT");
      long sent = slotParticipation(buncheolId, participantId, bundleId, "PAYMENT_SENT");
      long confirmed = slotParticipation(buncheolId, participantId, bundleId, "CONFIRMED");
      long otherBundleId = fixture.bundle(otherBuncheolId, participantId, addressId, HOUR.negated());
      long otherAwaiting =
          slotParticipation(otherBuncheolId, participantId, otherBundleId, "AWAITING_PAYMENT");

      int cancelled =
          transactionTemplate.execute(
              status ->
                  participationRepository.cancelUnconfirmedByBuncheolId(buncheolId, Instant.now()));

      assertThat(cancelled).isEqualTo(2);
      for (long id : new long[] {awaiting, sent}) {
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT CONCAT(status, '/', cancel_reason, '/', cancelled_at IS NOT NULL)"
                        + " FROM participations WHERE id = ?",
                    String.class,
                    id))
            .isEqualTo("CANCELLED/PAYMENT_NOT_CONFIRMED/1");
      }
      assertThat(participationStatus(confirmed)).isEqualTo("CONFIRMED");
      assertThat(participationStatus(otherAwaiting)).isEqualTo("AWAITING_PAYMENT");
    }

    private long slotParticipation(
        final long buncheolId, final long participantId, final long bundleId, final String status) {
      return fixture.participation(
          buncheolId,
          fixture.slot(buncheolId, fixture.groupMember(groupId)),
          participantId,
          bundleId,
          status,
          "C2C",
          HOUR.negated());
    }
  }

  private String status(final long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private Object finalizedAt(final long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT finalized_at FROM buncheols WHERE id = ?", Object.class, buncheolId);
  }

  private String participationStatus(final long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }
}
