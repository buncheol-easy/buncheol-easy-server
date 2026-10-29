package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.admin.application.AdminBuncheolCommandService;
import buncheoleasy.buncheol.application.participation.BundleConfirmResult;
import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 분철의 회원 개최 쓰기 두 개를 동시에 여러 번 부딪친다. 결정적이지 않은 보조 확인이고, 순서 보장은 {@link
 * BuncheolLockOrderMySqlTest} 가 맡는다. 분철 행에서 한 줄로 서면 데드락·잠금 대기 예외·409 가 없어야 한다.
 */
@DisplayName("회원 개최 쓰기 경합 반복 (MySQL)")
class BuncheolContentionMySqlTest extends MySqlContainerTestSupport {

  private static final int ROUNDS = 30;
  private static final Duration DAY = Duration.ofDays(1);

  @Autowired private ParticipationService participationService;
  @Autowired private ParticipationBundleService participationBundleService;
  @Autowired private BuncheolService buncheolService;
  @Autowired private AdminBuncheolCommandService adminBuncheolCommandService;

  private MySqlFixture fixture;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    fixture = new MySqlFixture(jdbcTemplate);
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void 같은_묶음의_두_자리를_동시에_취소해도_둘_다_취소되고_묶음이_닫힌다() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      long hostId = fixture.user(false);
      long participantId = fixture.user(false);
      long groupId = fixture.group();
      long addressId = fixture.shippingAddress(participantId);
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 1, null);
      long bundleId = fixture.bundle(buncheolId, participantId, addressId, null);
      long first =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "APPLIED",
              "C2C",
              null);
      long second =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "APPLIED",
              "C2C",
              null);

      failures.addAll(
          runTogether(
              () -> {
                participationService.cancelByParticipant(participantId, first);
                return null;
              },
              () -> {
                participationService.cancelByParticipant(participantId, second);
                return null;
              }));

      assertThat(statusOf(first)).isEqualTo("CANCELLED");
      assertThat(statusOf(second)).isEqualTo("CANCELLED");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT closed_at IS NOT NULL FROM participation_bundles WHERE id = ?",
                  Boolean.class,
                  bundleId))
          .isTrue();
    }
    assertThat(failures).isEmpty();
  }

  @Test
  void 같은_사람이_한_자리를_취소하며_다른_자리를_신청해도_묶음이_어긋나지_않는다() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      long hostId = fixture.user(false);
      long participantId = fixture.user(false);
      long groupId = fixture.group();
      long addressId = fixture.shippingAddress(participantId);
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 1, null);
      long bundleId = fixture.bundle(buncheolId, participantId, addressId, null);
      long existing =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "APPLIED",
              "C2C",
              null);
      long emptySlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      // 정원이 차지 않게 빈 자리를 하나 더 둔다 — 이 테스트는 상속과 묶음 닫기만 본다.
      fixture.slot(buncheolId, fixture.groupMember(groupId));

      failures.addAll(
          runTogether(
              () ->
                  participationService.participate(
                      buncheolId, participantId, new ParticipateRequest(emptySlotId, addressId)),
              () -> {
                participationService.cancelByParticipant(participantId, existing);
                return null;
              }));

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM participation_bundles pb WHERE pb.buncheol_id = ?"
                      + " AND pb.closed_at IS NOT NULL AND EXISTS (SELECT 1 FROM participations p"
                      + "   WHERE p.bundle_id = pb.id"
                      + "     AND p.status IN ('APPLIED','AWAITING_PAYMENT','PAYMENT_SENT','CONFIRMED'))",
                  Integer.class,
                  buncheolId))
          .as("닫힌 묶음에 살아 있는 참여가 없어야 한다")
          .isZero();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM participation_bundles pb WHERE pb.buncheol_id = ?"
                      + " AND pb.closed_at IS NULL AND NOT EXISTS (SELECT 1 FROM participations p"
                      + "   WHERE p.bundle_id = pb.id"
                      + "     AND p.status IN ('APPLIED','AWAITING_PAYMENT','PAYMENT_SENT','CONFIRMED'))",
                  Integer.class,
                  buncheolId))
          .as("열린 묶음에는 살아 있는 참여가 있어야 한다")
          .isZero();
    }
    assertThat(failures).isEmpty();
  }

  // 둘째 요청은 첫째가 막 만든 신청을 잠금 읽기로 보고 같은 묶음에 붙는다. 그 묶음의 배송지도 잠금 읽기로 읽어야 한다 — 일반 조회는
  // 잠금 전 스냅샷이라 막 만든 묶음이 안 보여 「배송지를 찾을 수 없음」으로 실패했다.
  @Test
  void 같은_사람이_모집_중_두_자리를_동시에_신청하면_한_묶음에_붙는다() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      long hostId = fixture.user(false);
      long participantId = fixture.user(false);
      long groupId = fixture.group();
      long addressId = fixture.shippingAddress(participantId);
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 1, null);
      long firstSlot = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long secondSlot = fixture.slot(buncheolId, fixture.groupMember(groupId));
      // 정원이 차지 않게 빈 자리를 하나 더 둔다 — 이 테스트는 상속만 본다.
      fixture.slot(buncheolId, fixture.groupMember(groupId));

      assertThat(
              runTogether(
                  () ->
                      participationService.participate(
                          buncheolId, participantId, new ParticipateRequest(firstSlot, addressId)),
                  () ->
                      participationService.participate(
                          buncheolId, participantId, new ParticipateRequest(secondSlot, addressId))))
          .isEmpty();

      assertThat(
              jdbcTemplate.queryForList(
                  "SELECT CONCAT(pb.shipping_fee, '/', pb.is_extra, '/',"
                      + " (SELECT COUNT(*) FROM participations p WHERE p.bundle_id = pb.id))"
                      + " FROM participation_bundles pb WHERE pb.buncheol_id = ?",
                  String.class,
                  buncheolId))
          .containsExactly("3000/0/2");
    }
  }

  // 마지막 두 묶음이 함께 확인되면 뒤에 커밋하는 쪽이 잠금 아래에서 다시 세어 진행 확정한다 — 확정은 한 번, 알림·배송은 빠짐없이.
  @Test
  void 남은_두_묶음을_동시에_입금_확인하면_진행_확정은_한_번이고_배송과_알림이_빠지지_않는다() throws Exception {
    List<Long> participants = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      long hostId = fixture.user(false);
      long groupId = fixture.group();
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "PAYMENT_COLLECTING", DAY, 1, DAY);
      long first = fixture.user(false);
      long second = fixture.user(false);
      long firstBundle = fixture.bundle(buncheolId, first, fixture.shippingAddress(first), DAY);
      long secondBundle = fixture.bundle(buncheolId, second, fixture.shippingAddress(second), DAY);
      long firstSlot =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              first,
              firstBundle,
              "AWAITING_PAYMENT",
              "C2C",
              DAY);
      long secondSlot =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              second,
              secondBundle,
              "PAYMENT_SENT",
              "C2C",
              DAY);
      participants.add(first);
      participants.add(second);

      List<Object> results = new ArrayList<>();
      assertThat(
              runTogether(
                  results,
                  () ->
                      participationBundleService.confirmPayment(
                          hostId, firstBundle, List.of(firstSlot)),
                  () ->
                      participationBundleService.confirmPayment(
                          hostId, secondBundle, List.of(secondSlot))))
          .as("데드락·잠금 대기 예외가 없어야 한다")
          .isEmpty();

      assertThat(
              results.stream()
                  .filter(result -> ((BundleConfirmResult) result).buncheolConfirmed())
                  .count())
          .as("진행 확정은 한 요청만")
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId))
          .isEqualTo("CONFIRMED");
      assertThat(
              jdbcTemplate.queryForList(
                  "SELECT bundle_id FROM deliveries WHERE bundle_id IN (?, ?)",
                  Long.class,
                  firstBundle,
                  secondBundle))
          .containsExactlyInAnyOrder(firstBundle, secondBundle);
    }
    assertThat(awaitConfirmedNoticeCounts(participants))
        .as("진행 확정 안내가 참여자마다 정확히 1건")
        .containsOnly(1);
  }

  @Nested
  @DisplayName("진행 확정 뒤 추가 모집 경합 (M1·M2·M4·M6·M7·M8·M9)")
  class ExtraRecruit {

    @Test
    void M1_두_사람이_같은_빈_자리를_동시에_추가_신청하면_한_명만_받는다() throws Exception {
      List<Throwable> unexpected = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        long slotId = fixture.slot(b.buncheolId(), fixture.groupMember(b.groupId()));
        long first = fixture.user(false);
        long firstAddress = fixture.shippingAddress(first);
        long second = fixture.user(false);
        long secondAddress = fixture.shippingAddress(second);

        List<Object> results = new ArrayList<>();
        List<Throwable> failures =
            runTogether(
                results,
                () ->
                    participationService.participate(
                        b.buncheolId(), first, new ParticipateRequest(slotId, firstAddress)),
                () ->
                    participationService.participate(
                        b.buncheolId(), second, new ParticipateRequest(slotId, secondAddress)));

        assertThat(results).hasSize(1);
        assertThat(failures).hasSize(1);
        unexpected.addAll(unexpected(failures, ErrorCode.PARTICIPATION_ALREADY_EXISTS));
        assertThat(liveCountOnSlot(slotId)).isEqualTo(1);
      }
      assertThat(unexpected).isEmpty();
    }

    @Test
    void M2_같은_사람이_두_자리를_동시에_추가_신청하면_한_묶음에_붙고_배송비는_한_번이다() throws Exception {
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        long firstSlot = fixture.slot(b.buncheolId(), fixture.groupMember(b.groupId()));
        long secondSlot = fixture.slot(b.buncheolId(), fixture.groupMember(b.groupId()));
        long participantId = fixture.user(false);
        long addressId = fixture.shippingAddress(participantId);

        assertThat(
                runTogether(
                    () ->
                        participationService.participate(
                            b.buncheolId(),
                            participantId,
                            new ParticipateRequest(firstSlot, addressId)),
                    () ->
                        participationService.participate(
                            b.buncheolId(),
                            participantId,
                            new ParticipateRequest(secondSlot, addressId))))
            .isEmpty();

        assertThat(
                jdbcTemplate.queryForList(
                    "SELECT CONCAT(pb.is_extra, '/', pb.shipping_fee, '/',"
                        + " (SELECT COUNT(*) FROM participations p WHERE p.bundle_id = pb.id"
                        + "   AND p.status = 'APPLIED'))"
                        + " FROM participation_bundles pb"
                        + " WHERE pb.buncheol_id = ? AND pb.participant_id = ?",
                    String.class,
                    b.buncheolId(),
                    participantId))
            .containsExactly("1/3000/2");
      }
    }

    @Test
    void M4_수락과_스위치_끄기를_동시에_하면_둘_다_된다() throws Exception {
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        PendingBundle pending = pendingExtraBundle(b, "APPLIED", null);

        assertThat(
                runTogether(
                    () -> {
                      participationBundleService.accept(
                          b.hostId(), pending.bundleId(), List.of(pending.participationId()));
                      return null;
                    },
                    () -> {
                      buncheolService.changeExtraRecruit(b.hostId(), b.buncheolId(), false);
                      return null;
                    }))
            .isEmpty();

        assertThat(statusOf(pending.participationId())).isEqualTo("AWAITING_PAYMENT");
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT extra_recruit_open FROM buncheols WHERE id = ?",
                    Integer.class,
                    b.buncheolId()))
            .isZero();
      }
    }

    @Test
    void M6_기한이_지난_추가_묶음을_입금_확인과_제외가_동시에_오면_하나만_반영된다() throws Exception {
      List<Throwable> unexpected = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        PendingBundle accepted = pendingExtraBundle(b, "AWAITING_PAYMENT", DAY.negated());

        List<Object> results = new ArrayList<>();
        List<Throwable> failures =
            runTogether(
                results,
                () ->
                    participationBundleService.confirmPayment(
                        b.hostId(), accepted.bundleId(), List.of(accepted.participationId())),
                () -> participationBundleService.release(b.hostId(), accepted.bundleId()));

        assertThat(results).hasSize(1);
        assertThat(failures).hasSize(1);
        unexpected.addAll(
            unexpected(
                failures,
                ErrorCode.BUNDLE_RELEASE_HAS_CONFIRMED,
                ErrorCode.BUNDLE_CONFIRM_NOT_ALLOWED));
        int deliveries = deliveryCount(accepted.bundleId());
        if (results.get(0) instanceof BundleConfirmResult) {
          assertThat(statusOf(accepted.participationId())).isEqualTo("CONFIRMED");
          assertThat(deliveries).isEqualTo(1);
        } else {
          assertThat(
                  jdbcTemplate.queryForObject(
                      "SELECT CONCAT(status, '/', cancel_reason) FROM participations WHERE id = ?",
                      String.class,
                      accepted.participationId()))
              .isEqualTo("CANCELLED/HOST_RELEASED");
          assertThat(bundleClosed(accepted.bundleId())).isTrue();
          assertThat(deliveries).isZero();
        }
      }
      assertThat(unexpected).isEmpty();
    }

    @Test
    void M7_추가_신청과_운영진_파기가_동시에_오면_파기된_분철에_살아_있는_참여가_없다() throws Exception {
      List<Throwable> unexpected = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        long slotId = fixture.slot(b.buncheolId(), fixture.groupMember(b.groupId()));
        long participantId = fixture.user(false);
        long addressId = fixture.shippingAddress(participantId);

        List<Throwable> failures =
            runTogether(
                () ->
                    participationService.participate(
                        b.buncheolId(), participantId, new ParticipateRequest(slotId, addressId)),
                () -> {
                  adminBuncheolCommandService.cancel(b.buncheolId());
                  return null;
                });

        unexpected.addAll(unexpected(failures, ErrorCode.BUNCHEOL_NOT_RECRUITING));
        assertCancelledWithoutLiveParticipations(b.buncheolId());
      }
      assertThat(unexpected).isEmpty();
    }

    @Test
    void M8_운영진_파기가_수락이나_추가_묶음_입금_확인과_겹쳐도_살아_있는_참여와_배송이_남지_않는다()
        throws Exception {
      List<Throwable> unexpected = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        PendingBundle pending = pendingExtraBundle(b, "APPLIED", null);
        unexpected.addAll(
            unexpected(
                runTogether(
                    () -> {
                      adminBuncheolCommandService.cancel(b.buncheolId());
                      return null;
                    },
                    () -> {
                      participationBundleService.accept(
                          b.hostId(), pending.bundleId(), List.of(pending.participationId()));
                      return null;
                    }),
                ErrorCode.BUNDLE_ACCEPT_NOT_ALLOWED));
        assertCancelledWithoutLiveParticipations(b.buncheolId());

        ExtraBuncheol c = extraRecruitingBuncheol();
        PendingBundle accepted = pendingExtraBundle(c, "AWAITING_PAYMENT", DAY);
        unexpected.addAll(
            unexpected(
                runTogether(
                    () -> {
                      adminBuncheolCommandService.cancel(c.buncheolId());
                      return null;
                    },
                    () ->
                        participationBundleService.confirmPayment(
                            c.hostId(), accepted.bundleId(), List.of(accepted.participationId()))),
                ErrorCode.BUNDLE_CONFIRM_NOT_ALLOWED));
        assertCancelledWithoutLiveParticipations(c.buncheolId());
        assertThat(deliveryCount(accepted.bundleId())).isZero();
      }
      assertThat(unexpected).isEmpty();
    }

    @Test
    void M9_같은_묶음을_두_번_수락하면_한_번만_되고_수락_안내도_한_통이다() throws Exception {
      List<Throwable> unexpected = new ArrayList<>();
      List<Long> participants = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        ExtraBuncheol b = extraRecruitingBuncheol();
        PendingBundle pending = pendingExtraBundle(b, "APPLIED", null);
        participants.add(pending.participantId());

        List<Throwable> failures =
            runTogether(
                () -> {
                  participationBundleService.accept(
                      b.hostId(), pending.bundleId(), List.of(pending.participationId()));
                  return null;
                },
                () -> {
                  participationBundleService.accept(
                      b.hostId(), pending.bundleId(), List.of(pending.participationId()));
                  return null;
                });

        assertThat(failures).hasSize(1);
        unexpected.addAll(unexpected(failures, ErrorCode.BUNDLE_ACCEPT_NOT_ALLOWED));
        assertThat(statusOf(pending.participationId())).isEqualTo("AWAITING_PAYMENT");
      }
      assertThat(unexpected).isEmpty();
      assertThat(awaitInboxCounts(participants, "추가 신청 수락 안내"))
          .as("수락 안내가 묶음마다 정확히 1건")
          .containsOnly(1);
    }

    private void assertCancelledWithoutLiveParticipations(final long buncheolId) {
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId))
          .isEqualTo("HOST_CANCELLED");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM participations WHERE buncheol_id = ?"
                      + " AND status IN ('APPLIED','AWAITING_PAYMENT','PAYMENT_SENT','CONFIRMED')",
                  Integer.class,
                  buncheolId))
          .isZero();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM deliveries d JOIN participation_bundles pb"
                      + " ON pb.id = d.bundle_id WHERE pb.buncheol_id = ?",
                  Integer.class,
                  buncheolId))
          .isZero();
    }
  }

  private record ExtraBuncheol(long hostId, long groupId, long buncheolId) {}

  private record PendingBundle(long participantId, long bundleId, long participationId) {}

  /** 추가 모집 중인 진행 확정 회원 개최. 다른 사람의 확인된 참여가 배송 없이 남아 「종료」가 아니고, 계좌 스냅샷이 있다. */
  private ExtraBuncheol extraRecruitingBuncheol() {
    long hostId = fixture.user(false);
    long groupId = fixture.group();
    long buncheolId =
        fixture.buncheol(hostId, groupId, "C2C", "CONFIRMED", DAY.negated(), 1, DAY.negated());
    long otherId = fixture.user(false);
    long otherBundleId =
        fixture.bundle(buncheolId, otherId, fixture.shippingAddress(otherId), DAY.negated());
    fixture.participation(
        buncheolId,
        fixture.slot(buncheolId, fixture.groupMember(groupId)),
        otherId,
        otherBundleId,
        "CONFIRMED",
        "C2C",
        DAY.negated());
    fixture.openExtraRecruit(buncheolId);
    return new ExtraBuncheol(hostId, groupId, buncheolId);
  }

  /** 추가 신청 묶음(자리 1). 상태가 입금 대기면 {@code due} 로 묶음·참여 기한을 채운다. */
  private PendingBundle pendingExtraBundle(
      final ExtraBuncheol b, final String status, final Duration due) {
    long participantId = fixture.user(false);
    long bundleId =
        fixture.bundle(b.buncheolId(), participantId, fixture.shippingAddress(participantId), due);
    fixture.markExtra(bundleId);
    long participationId =
        fixture.participation(
            b.buncheolId(),
            fixture.slot(b.buncheolId(), fixture.groupMember(b.groupId())),
            participantId,
            bundleId,
            status,
            "C2C",
            due);
    return new PendingBundle(participantId, bundleId, participationId);
  }

  // 반복 경합에서 기대한 업무 오류(409)는 결과의 일부다. 그 밖의 예외(잠금 대기·데드락 등)만 모은다.
  private static List<Throwable> unexpected(
      final List<Throwable> failures, final ErrorCode... allowed) {
    Set<ErrorCode> ok = Set.of(allowed);
    return failures.stream()
        .filter(f -> !(f instanceof BusinessException be && ok.contains(be.getErrorCode())))
        .toList();
  }

  private int liveCountOnSlot(final long slotId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM participations WHERE buncheol_member_id = ?"
            + " AND status IN ('APPLIED','AWAITING_PAYMENT','PAYMENT_SENT','CONFIRMED')",
        Integer.class,
        slotId);
  }

  private int deliveryCount(final long bundleId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM deliveries WHERE bundle_id = ?", Integer.class, bundleId);
  }

  private boolean bundleClosed(final long bundleId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT closed_at IS NOT NULL FROM participation_bundles WHERE id = ?",
            Boolean.class,
            bundleId));
  }

  private List<Integer> awaitInboxCounts(final List<Long> recipientIds, final String title)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (true) {
      List<Integer> counts =
          recipientIds.stream()
              .map(
                  id ->
                      jdbcTemplate.queryForObject(
                          "SELECT COUNT(*) FROM inbox_messages WHERE recipient_id = ? AND title = ?",
                          Integer.class,
                          id,
                          title))
              .toList();
      if (counts.stream().allMatch(count -> count > 0) || System.nanoTime() > deadline) {
        // 늦게 오는 중복이 있으면 잡히도록 큐가 빌 틈을 조금 더 준다.
        Thread.sleep(300);
        return recipientIds.stream()
            .map(
                id ->
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inbox_messages WHERE recipient_id = ? AND title = ?",
                        Integer.class,
                        id,
                        title))
            .toList();
      }
      Thread.sleep(50);
    }
  }

  private List<Integer> awaitConfirmedNoticeCounts(final List<Long> recipientIds)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    List<Integer> counts;
    do {
      counts =
          recipientIds.stream()
              .map(
                  id ->
                      jdbcTemplate.queryForObject(
                          "SELECT COUNT(*) FROM inbox_messages"
                              + " WHERE recipient_id = ? AND title = '분철 진행 확정 안내'",
                          Integer.class,
                          id))
              .toList();
      if (counts.stream().allMatch(count -> count > 0)) {
        // 늦게 오는 중복이 있으면 잡히도록 큐가 빌 틈을 조금 더 준다.
        Thread.sleep(300);
        return recipientIds.stream()
            .map(
                id ->
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inbox_messages"
                            + " WHERE recipient_id = ? AND title = '분철 진행 확정 안내'",
                        Integer.class,
                        id))
            .toList();
      }
      Thread.sleep(50);
    } while (System.nanoTime() < deadline);
    return counts;
  }

  private List<Throwable> runTogether(final Callable<?> first, final Callable<?> second)
      throws InterruptedException {
    return runTogether(new ArrayList<>(), first, second);
  }

  /** 두 작업을 동시에 시작하고 끝난 값은 {@code results} 에, 실패는 돌려준다. */
  private List<Throwable> runTogether(
      final List<Object> results, final Callable<?> first, final Callable<?> second)
      throws InterruptedException {
    CyclicBarrier barrier = new CyclicBarrier(2);
    List<Future<?>> futures = new ArrayList<>();
    for (Callable<?> task : List.of(first, second)) {
      futures.add(
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return task.call();
              }));
    }
    List<Throwable> failures = new ArrayList<>();
    for (Future<?> future : futures) {
      try {
        results.add(future.get(30, TimeUnit.SECONDS));
      } catch (ExecutionException e) {
        failures.add(e.getCause());
      } catch (Exception e) {
        failures.add(e);
      }
    }
    return failures;
  }

  private String statusOf(final long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }
}
