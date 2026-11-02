package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
  @Autowired private ParticipationBundleRepository participationBundleRepository;
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
  @DisplayName("운영진 파기 CAS staffCancelIfStatus")
  class StaffCancelIfStatus {

    // 입금 확인 건수와 무관하다 — 확인된 참여가 있어도 파기된다(연쇄 취소는 호출자가 한다).
    @Test
    void 회원_개최_입금_수집_중이면_확인된_참여가_있어도_숨기고_마감_판정_시각을_남긴다() {
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", HOUR.negated(), 1, HOUR.negated());
      long participantId = fixture.user(false);
      long bundleId =
          fixture.bundle(buncheolId, participantId, fixture.shippingAddress(participantId), HOUR);
      fixture.participation(
          buncheolId,
          fixture.slot(buncheolId, fixture.groupMember(groupId)),
          participantId,
          bundleId,
          "CONFIRMED",
          "C2C",
          HOUR);

      assertThat(staffCancel(buncheolId, BuncheolStatus.PAYMENT_COLLECTING)).isEqualTo(1);
      assertThat(status(buncheolId)).isEqualTo("HOST_CANCELLED");
      assertThat(finalizedAt(buncheolId)).isNotNull();
    }

    @Test
    void 회원_개최_진행_확정이면_진행_확정을_기대할_때_숨긴다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());

      assertThat(staffCancel(buncheolId, BuncheolStatus.CONFIRMED)).isEqualTo(1);
      assertThat(status(buncheolId)).isEqualTo("HOST_CANCELLED");
      assertThat(finalizedAt(buncheolId)).isNotNull();
    }

    @Test
    void 기대한_상태가_아니면_건드리지_않는다() {
      long recruiting = buncheol("C2C", "RECRUITING", HOUR);
      long confirmed = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long collecting =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", HOUR.negated(), 1, HOUR);

      assertThat(staffCancel(recruiting, BuncheolStatus.PAYMENT_COLLECTING)).isZero();
      assertThat(staffCancel(confirmed, BuncheolStatus.PAYMENT_COLLECTING)).isZero();
      assertThat(staffCancel(collecting, BuncheolStatus.CONFIRMED)).isZero();
      assertThat(status(recruiting)).isEqualTo("RECRUITING");
      assertThat(status(confirmed)).isEqualTo("CONFIRMED");
      assertThat(status(collecting)).isEqualTo("PAYMENT_COLLECTING");
    }

    @Test
    void 운영진_분철이면_상태가_맞아도_건드리지_않는다() {
      long collecting =
          fixture.buncheol(hostId, groupId, "LEGACY", "PAYMENT_COLLECTING", HOUR, 1, HOUR);
      long confirmed = buncheol("LEGACY", "CONFIRMED", HOUR.negated());

      assertThat(staffCancel(collecting, BuncheolStatus.PAYMENT_COLLECTING)).isZero();
      assertThat(staffCancel(confirmed, BuncheolStatus.CONFIRMED)).isZero();
      assertThat(status(collecting)).isEqualTo("PAYMENT_COLLECTING");
      assertThat(status(confirmed)).isEqualTo("CONFIRMED");
    }

    private Integer staffCancel(final long buncheolId, final BuncheolStatus expected) {
      return transactionTemplate.execute(
          status -> buncheolRepository.staffCancelIfStatus(buncheolId, expected, Instant.now()));
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

  @Nested
  @DisplayName("추가 신청 조건부 INSERT saveIfExtraRecruiting")
  class SaveIfExtraRecruiting {

    @Test
    void 진행_확정된_회원_개최가_스위치를_켰으면_원래_마감이_지났어도_넣는다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      openExtraRecruit(buncheolId);

      Participation applied = applied(buncheolId);
      Boolean inserted =
          transactionTemplate.execute(status -> participationRepository.saveIfExtraRecruiting(applied));

      assertThat(inserted).isTrue();
      assertThat(applied.getId()).isNotNull();
      assertThat(participationStatus(applied.getId())).isEqualTo("APPLIED");
    }

    @Test
    void 스위치가_꺼졌거나_진행_확정이_아니거나_운영진_분철이면_넣지_않는다() {
      long switchOff = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long collecting = buncheol("C2C", "PAYMENT_COLLECTING", HOUR.negated());
      openExtraRecruit(collecting);
      long staff = buncheol("LEGACY", "CONFIRMED", HOUR.negated());
      openExtraRecruit(staff);

      for (long buncheolId : new long[] {switchOff, collecting, staff}) {
        Participation applied = applied(buncheolId);
        Boolean inserted =
            transactionTemplate.execute(
                status -> participationRepository.saveIfExtraRecruiting(applied));
        assertThat(inserted).as("buncheol %d", buncheolId).isFalse();
      }
      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participations", Long.class))
          .isZero();
    }

    private void openExtraRecruit(final long buncheolId) {
      jdbcTemplate.update("UPDATE buncheols SET extra_recruit_open = 1 WHERE id = ?", buncheolId);
    }

    private Participation applied(final long buncheolId) {
      long participantId = fixture.user(false);
      return Participation.createApplied(
          buncheolId,
          fixture.slot(buncheolId, fixture.groupMember(groupId)),
          participantId,
          fixture.shippingAddress(participantId),
          15_000L,
          0L);
    }
  }

  @Nested
  @DisplayName("추가 신청 수락·거절 CAS")
  class AcceptReject {

    @Test
    void 수락은_그_묶음의_신청만_입금_대기로_넘기고_묶음과_참여에_기한을_채운다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long participantId = fixture.user(false);
      long addressId = fixture.shippingAddress(participantId);
      long bundleId = fixture.bundle(buncheolId, participantId, addressId, null);
      long applied = slotParticipation(buncheolId, participantId, bundleId, "APPLIED");
      long otherBundleId = fixture.bundle(buncheolId, participantId, addressId, null);
      long otherApplied = slotParticipation(buncheolId, participantId, otherBundleId, "APPLIED");
      Instant dueAt = Instant.now().plus(Duration.ofHours(24));

      Integer accepted =
          transactionTemplate.execute(
              status -> {
                int moved = participationRepository.acceptBundleIfApplied(bundleId, dueAt, Instant.now());
                assertThat(participationBundleRepository.assignDueAt(bundleId, dueAt, Instant.now()))
                    .isEqualTo(1);
                // 이미 기한이 있으면 다시 채우지 않는다.
                assertThat(participationBundleRepository.assignDueAt(bundleId, dueAt, Instant.now()))
                    .isZero();
                return moved;
              });

      assertThat(accepted).isEqualTo(1);
      assertThat(participationStatus(applied)).isEqualTo("AWAITING_PAYMENT");
      assertThat(participationStatus(otherApplied)).isEqualTo("APPLIED");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT (SELECT due_at IS NOT NULL FROM participations WHERE id = ?)"
                      + " + (SELECT due_at IS NOT NULL FROM participation_bundles WHERE id = ?)",
                  Integer.class,
                  applied,
                  bundleId))
          .isEqualTo(2);
    }

    // 묶음 표를 서브쿼리로 보는 UPDATE 가 MySQL 에서 도는지 본다(같은 표를 보면 error 1093, H2 는 허용해 안 드러난다).
    @Test
    void 제외는_기한이_지난_열린_묶음의_입금_대기와_보냈어요만_취소한다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long participantId = fixture.user(false);
      long addressId = fixture.shippingAddress(participantId);
      long dueBundle = fixture.bundle(buncheolId, participantId, addressId, HOUR.negated());
      long awaiting = slotParticipation(buncheolId, participantId, dueBundle, "AWAITING_PAYMENT");
      long sent = slotParticipation(buncheolId, participantId, dueBundle, "PAYMENT_SENT");
      long beforeDueBundle = fixture.bundle(buncheolId, participantId, addressId, HOUR);
      long beforeDue = slotParticipation(buncheolId, participantId, beforeDueBundle, "AWAITING_PAYMENT");

      Integer released =
          transactionTemplate.execute(
              status -> participationRepository.releaseBundleIfDue(dueBundle, Instant.now()));
      Integer notYet =
          transactionTemplate.execute(
              status -> participationRepository.releaseBundleIfDue(beforeDueBundle, Instant.now()));

      assertThat(released).isEqualTo(2);
      assertThat(notYet).isZero();
      for (long id : new long[] {awaiting, sent}) {
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT CONCAT(status, '/', cancel_reason) FROM participations WHERE id = ?",
                    String.class,
                    id))
            .isEqualTo("CANCELLED/HOST_RELEASED");
      }
      assertThat(participationStatus(beforeDue)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 거절은_그_묶음의_신청만_개최자_거절로_취소한다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long participantId = fixture.user(false);
      long addressId = fixture.shippingAddress(participantId);
      long bundleId = fixture.bundle(buncheolId, participantId, addressId, null);
      long applied = slotParticipation(buncheolId, participantId, bundleId, "APPLIED");
      long confirmed = slotParticipation(buncheolId, participantId, bundleId, "CONFIRMED");

      Integer rejected =
          transactionTemplate.execute(
              status -> participationRepository.rejectBundleIfApplied(bundleId, Instant.now()));

      assertThat(rejected).isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT CONCAT(status, '/', cancel_reason, '/', cancelled_at IS NOT NULL)"
                      + " FROM participations WHERE id = ?",
                  String.class,
                  applied))
          .isEqualTo("CANCELLED/HOST_REJECTED/1");
      assertThat(participationStatus(confirmed)).isEqualTo("CONFIRMED");
    }

    @Test
    void 수락_대기_묶음_수는_신청이_든_묶음을_한_번씩_센다() {
      long buncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long otherBuncheolId = buncheol("C2C", "CONFIRMED", HOUR.negated());
      long participantId = fixture.user(false);
      long addressId = fixture.shippingAddress(participantId);
      long twoSlotBundle = fixture.bundle(buncheolId, participantId, addressId, null);
      slotParticipation(buncheolId, participantId, twoSlotBundle, "APPLIED");
      slotParticipation(buncheolId, participantId, twoSlotBundle, "APPLIED");
      long oneSlotBundle = fixture.bundle(buncheolId, participantId, addressId, null);
      slotParticipation(buncheolId, participantId, oneSlotBundle, "APPLIED");
      long acceptedBundle = fixture.bundle(buncheolId, participantId, addressId, HOUR);
      slotParticipation(buncheolId, participantId, acceptedBundle, "AWAITING_PAYMENT");

      assertThat(
              participationRepository.countAppliedBundlesByBuncheolIds(
                  List.of(buncheolId, otherBuncheolId)))
          .extracting(c -> c.buncheolId() + ":" + c.count())
          .containsExactly(buncheolId + ":2");
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
          null);
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
