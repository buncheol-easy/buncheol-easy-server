package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

  private List<Throwable> runTogether(final Callable<?> first, final Callable<?> second)
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
        future.get(30, TimeUnit.SECONDS);
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
