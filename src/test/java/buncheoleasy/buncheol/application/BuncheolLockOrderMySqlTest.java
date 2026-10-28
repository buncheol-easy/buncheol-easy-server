package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.admin.application.AdminBuncheolCommandService;
import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolConfirmRequest;
import buncheoleasy.buncheol.dto.request.BuncheolConfirmRequest.CancelParticipation;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.delivery.application.DeliveryService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 분철 단위 쓰기가 분철 행을 <b>첫 잠금</b>으로 잡는지 경로마다 확인한다(BuncheolDomainService#getBuncheolForUpdate).
 *
 * <p>데드락을 운에 맡겨 일으키지 않는다. 다른 연결이 분철 행을 쥔 채로 경로를 부르고, 그 경로가 분철 행을 기다리는 줄에 섰을 때 아직 참여·묶음
 * 행을 잡지 않았는지를 {@code NOWAIT} 로 본다. 기다림은 {@code performance_schema.data_lock_waits} 로 확인해 시간에 기대지
 * 않는다. 경로를 새로 더하면 여기에 한 줄 더한다.
 */
@DisplayName("분철 행 먼저 잠그기 (MySQL)")
class BuncheolLockOrderMySqlTest extends MySqlContainerTestSupport {

  private static final Duration DAY = Duration.ofDays(1);
  private static final String CONFIRMED_NOTICE = "분철 진행 확정 안내";

  @Autowired private DataSource dataSource;
  @Autowired private ParticipationService participationService;
  @Autowired private ParticipationBundleService participationBundleService;
  @Autowired private BuncheolService buncheolService;
  @Autowired private BuncheolAutoCloseService buncheolAutoCloseService;
  @Autowired private AdminBuncheolCommandService adminBuncheolCommandService;
  @Autowired private DeliveryService deliveryService;

  private MySqlFixture fixture;
  private ExecutorService executor;

  private long hostId;
  private long participantId;
  private long groupId;
  private long shippingAddressId;

  @BeforeEach
  void setUp() {
    fixture = new MySqlFixture(jdbcTemplate);
    executor = Executors.newSingleThreadExecutor();
    hostId = fixture.user(false);
    participantId = fixture.user(false);
    groupId = fixture.group();
    shippingAddressId = fixture.shippingAddress(participantId);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Nested
  @DisplayName("회원 개최")
  class Member {

    @Test
    void 참여자_자발_취소는_분철_행을_먼저_기다린다() throws Exception {
      long buncheolId = buncheol("C2C", "RECRUITING", null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      long participationId =
          fixture.participation(buncheolId, slotId, participantId, bundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            participationService.cancelByParticipant(participantId, participationId);
            return null;
          });

      assertThat(statusOf("participations", participationId)).isEqualTo("CANCELLED");
      assertThat(bundleClosed(bundleId)).isTrue();
    }

    @Test
    void 묶음_보냈어요는_분철_행을_먼저_기다린다() throws Exception {
      long buncheolId = buncheol("C2C", "PAYMENT_COLLECTING", DAY);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, DAY);
      long participationId =
          fixture.participation(
              buncheolId, slotId, participantId, bundleId, "AWAITING_PAYMENT", "C2C", DAY);

      assertLocksBuncheolFirst(
          buncheolId, () -> participationBundleService.markPaymentSent(participantId, bundleId));

      assertThat(statusOf("participations", participationId)).isEqualTo("PAYMENT_SENT");
    }

    @Test
    void 묶음_입금_확인은_분철_행을_먼저_기다린다() throws Exception {
      long buncheolId = buncheol("C2C", "PAYMENT_COLLECTING", DAY);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long otherSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, DAY);
      long participationId =
          fixture.participation(
              buncheolId, slotId, participantId, bundleId, "PAYMENT_SENT", "C2C", DAY);
      long otherBundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, DAY);
      fixture.participation(
          buncheolId, otherSlotId, participantId, otherBundleId, "AWAITING_PAYMENT", "C2C", DAY);

      assertLocksBuncheolFirst(
          buncheolId,
          () ->
              participationBundleService.confirmPayment(
                  hostId, bundleId, List.of(participationId)));

      assertThat(statusOf("participations", participationId)).isEqualTo("CONFIRMED");
    }

    @Test
    void 마지막_미확인_묶음_입금_확인은_분철_행을_먼저_기다리고_진행_확정까지_한다() throws Exception {
      long buncheolId = buncheol("C2C", "PAYMENT_COLLECTING", DAY);
      long confirmedBundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, DAY);
      long confirmedId =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              confirmedBundleId,
              "CONFIRMED",
              "C2C",
              DAY);
      long otherId = fixture.user(false);
      long lastBundleId =
          fixture.bundle(buncheolId, otherId, fixture.shippingAddress(otherId), DAY);
      long lastId =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              otherId,
              lastBundleId,
              "PAYMENT_SENT",
              "C2C",
              DAY);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> participationBundleService.confirmPayment(hostId, lastBundleId, List.of(lastId)),
          List.of(confirmedId, lastId));

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("CONFIRMED");
      assertThat(statusOf("participations", lastId)).isEqualTo("CONFIRMED");
      assertThat(deliveryBundleIds(buncheolId))
          .containsExactlyInAnyOrder(confirmedBundleId, lastBundleId);
    }

    @Test
    void 모집_중_개최자_삭제는_분철_행을_먼저_기다린다() throws Exception {
      long buncheolId = buncheol("C2C", "RECRUITING", null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      long participationId =
          fixture.participation(buncheolId, slotId, participantId, bundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            buncheolService.cancelBuncheol(hostId, buncheolId);
            return null;
          });

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("HOST_CANCELLED");
      assertThat(statusOf("participations", participationId)).isEqualTo("CANCELLED");
    }

    @Test
    void 마지막_열린_자리_신청은_분철_행을_먼저_기다리고_입금_단계를_시작한다() throws Exception {
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 2, null);
      long firstSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long lastSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long otherId = fixture.user(false);
      long otherBundleId =
          fixture.bundle(buncheolId, otherId, fixture.shippingAddress(otherId), null);
      long otherParticipationId =
          fixture.participation(
              buncheolId, firstSlotId, otherId, otherBundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId,
          () ->
              participationService.participate(
                  buncheolId, participantId, new ParticipateRequest(lastSlotId, shippingAddressId)));

      assertPaymentStarted(buncheolId);
      assertThat(statusOf("participations", otherParticipationId)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 자리_빼기로_정원이_차면_분철_행을_먼저_기다리고_입금_단계를_시작한다() throws Exception {
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 3, null);
      long firstSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long secondSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long emptySlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      fixture.participation(buncheolId, firstSlotId, participantId, bundleId, "APPLIED", "C2C", null);
      fixture.participation(
          buncheolId, secondSlotId, participantId, bundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            buncheolService.removeMember(hostId, buncheolId, emptySlotId);
            return null;
          });

      // 최소 인원 3 → 열린 자리 2 로 낮춘 값으로 판정해야 시작한다.
      assertPaymentStarted(buncheolId);
    }

    // 취소 기록이 남은 자리는 지우지 못해 닫는다. 닫힌 자리를 열린 자리로 세면 정원이 차지 않는다 — 앞서 잠금 조회로 올린 자리
    // 엔티티가 아니라 닫기 뒤에 다시 읽은 자리로 세야 한다(최소 인원 UPDATE 가 영속성 컨텍스트를 비운다는 전제).
    @Test
    void 취소_기록이_남은_빈_자리를_빼면_자리가_닫히고_입금_단계를_시작한다() throws Exception {
      long buncheolId = fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", DAY, 3, null);
      long firstSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long secondSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long leftSlotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      fixture.participation(
          buncheolId, firstSlotId, participantId, bundleId, "APPLIED", "C2C", null);
      fixture.participation(
          buncheolId, secondSlotId, participantId, bundleId, "APPLIED", "C2C", null);
      long leftId = fixture.user(false);
      long leftBundleId = fixture.bundle(buncheolId, leftId, fixture.shippingAddress(leftId), null);
      long leftParticipationId =
          fixture.participation(
              buncheolId, leftSlotId, leftId, leftBundleId, "CANCELLED", "C2C", null);
      jdbcTemplate.update(
          "UPDATE participations"
              + " SET cancel_reason = 'USER_CANCELLED', cancelled_at = UTC_TIMESTAMP()"
              + " WHERE id = ?",
          leftParticipationId);
      jdbcTemplate.update(
          "UPDATE participation_bundles SET closed_at = UTC_TIMESTAMP() WHERE id = ?",
          leftBundleId);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            buncheolService.removeMember(hostId, buncheolId, leftSlotId);
            return null;
          });

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT access_type FROM buncheol_members WHERE id = ?",
                  String.class,
                  leftSlotId))
          .isEqualTo("CLOSED");
      assertPaymentStarted(buncheolId);
      assertThat(statusOf("participations", leftParticipationId)).isEqualTo("CANCELLED");
    }

    @Test
    void 마감_판정은_분철_행을_먼저_기다리고_최소_인원_이상이면_입금_단계를_시작한다() throws Exception {
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", Duration.ofHours(-1), 1, null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      fixture.participation(buncheolId, slotId, participantId, bundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId, () -> buncheolAutoCloseService.finalizeExpired(buncheolId, Instant.now()));

      assertPaymentStarted(buncheolId);
    }

    @Test
    void 진행_확정은_분철_행을_먼저_기다리고_확인된_묶음마다_배송을_하나_만든다() throws Exception {
      // 입금 기한이 지났고, 참여자 A 의 묶음(자리 2)은 입금 확인, B 의 묶음은 「보냈어요」만 눌렀다.
      long buncheolId =
          fixture.buncheol(
              hostId, groupId, "C2C", "PAYMENT_COLLECTING", Duration.ofHours(-2), 2, Duration.ofHours(-1));
      long confirmedBundle =
          fixture.bundle(buncheolId, participantId, shippingAddressId, Duration.ofHours(-1));
      long firstConfirmed =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              confirmedBundle,
              "CONFIRMED",
              "C2C",
              Duration.ofHours(-1));
      long secondConfirmed =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              confirmedBundle,
              "CONFIRMED",
              "C2C",
              Duration.ofHours(-1));
      long otherId = fixture.user(false);
      long sentBundle =
          fixture.bundle(buncheolId, otherId, fixture.shippingAddress(otherId), Duration.ofHours(-1));
      long sent =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              otherId,
              sentBundle,
              "PAYMENT_SENT",
              "C2C",
              Duration.ofHours(-1));
      Object finalizedBefore =
          jdbcTemplate.queryForObject(
              "SELECT finalized_at FROM buncheols WHERE id = ?", Object.class, buncheolId);

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            buncheolService.confirmBuncheol(
                hostId,
                buncheolId,
                new BuncheolConfirmRequest(
                    List.of(new CancelParticipation(sent, ParticipationStatus.PAYMENT_SENT))));
            return null;
          });

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("CONFIRMED");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT finalized_at FROM buncheols WHERE id = ?", Object.class, buncheolId))
          .isEqualTo(finalizedBefore);
      assertThat(statusOf("participations", firstConfirmed)).isEqualTo("CONFIRMED");
      assertThat(statusOf("participations", secondConfirmed)).isEqualTo("CONFIRMED");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT cancel_reason FROM participations WHERE id = ?", String.class, sent))
          .isEqualTo("PAYMENT_NOT_CONFIRMED");
      assertThat(bundleClosed(sentBundle)).isTrue();
      assertThat(bundleClosed(confirmedBundle)).isFalse();
      // 묶음당 배송 1개 — 확인된 묶음에만.
      assertThat(
              jdbcTemplate.queryForList(
                  "SELECT bundle_id FROM deliveries WHERE bundle_id IN (?, ?)",
                  Long.class,
                  confirmedBundle,
                  sentBundle))
          .containsExactly(confirmedBundle);
    }

    @Test
    void 운영진_파기는_분철_행을_먼저_기다리고_확인된_참여까지_전원_취소한다() throws Exception {
      long buncheolId =
          fixture.buncheol(
              hostId, groupId, "C2C", "PAYMENT_COLLECTING", Duration.ofHours(-2), 1, Duration.ofHours(-1));
      long bundleId =
          fixture.bundle(buncheolId, participantId, shippingAddressId, Duration.ofHours(-1));
      long confirmed =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "CONFIRMED",
              "C2C",
              Duration.ofHours(-1));
      long sent =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "PAYMENT_SENT",
              "C2C",
              Duration.ofHours(-1));

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            adminBuncheolCommandService.cancel(buncheolId);
            return null;
          });

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("HOST_CANCELLED");
      for (long id : new long[] {confirmed, sent}) {
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT CONCAT(status, '/', cancel_reason) FROM participations WHERE id = ?",
                    String.class,
                    id))
            .isEqualTo("CANCELLED/BUNCHEOL_CANCELLED");
      }
      assertThat(bundleClosed(bundleId)).isTrue();
    }

    @Test
    void 진행_확정_분철_운영진_파기는_분철_행을_먼저_기다리고_운송장_전_배송을_지운다() throws Exception {
      ConfirmedWithDeliveries confirmed = confirmedWithDeliveries();

      assertLocksBuncheolFirst(
          confirmed.buncheolId(),
          () -> {
            adminBuncheolCommandService.cancel(confirmed.buncheolId());
            return null;
          },
          confirmed.participationIds());

      assertCancelledWithoutDeliveries(confirmed);
    }

    // 계좌가 공개된 뒤에는 개최자 혼자 접지 않는다 — 분철 행을 먼저 잠근 뒤 BCH-126 으로 거절하고 아무것도 바꾸지 않는다.
    @Test
    void 입금_수집_중_개최자_삭제는_분철_행을_먼저_기다리고_운영진_경유로_거절한다() throws Exception {
      long buncheolId =
          fixture.buncheol(
              hostId, groupId, "C2C", "PAYMENT_COLLECTING", Duration.ofHours(-2), 1, Duration.ofHours(-1));
      long bundleId =
          fixture.bundle(buncheolId, participantId, shippingAddressId, Duration.ofHours(-1));
      long awaiting =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleId,
              "AWAITING_PAYMENT",
              "C2C",
              Duration.ofHours(-1));

      assertThatThrownBy(
              () ->
                  assertLocksBuncheolFirst(
                      buncheolId,
                      () -> {
                        buncheolService.cancelBuncheol(hostId, buncheolId);
                        return null;
                      }))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NEEDS_STAFF);

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("PAYMENT_COLLECTING");
      assertThat(statusOf("participations", awaiting)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 마감_판정은_최소_인원_미달이면_분철과_신청을_함께_취소한다() throws Exception {
      long buncheolId =
          fixture.buncheol(hostId, groupId, "C2C", "RECRUITING", Duration.ofHours(-1), 2, null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, null);
      long participationId =
          fixture.participation(buncheolId, slotId, participantId, bundleId, "APPLIED", "C2C", null);

      assertLocksBuncheolFirst(
          buncheolId, () -> buncheolAutoCloseService.finalizeExpired(buncheolId, Instant.now()));

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("CANCELLED");
      assertThat(statusOf("participations", participationId)).isEqualTo("CANCELLED");
      assertThat(bundleClosed(bundleId)).isTrue();
    }
  }

  @Nested
  @DisplayName("잠금을 기다린 뒤 읽기")
  class AfterLockWait {

    // 묶음 입금 확인은 분철 행 잠금 앞에서 묶음을 일반 조회한다. REPEATABLE READ 면 그 조회가 만든 스냅샷이 남아, 잠금을 기다리는 사이
    // 다른 탭에서 확인된 묶음 A 의 참여가 진행확정 알림 대상(일반 조회)에서 빠진다.
    @Test
    void 분철_행을_기다리는_사이_다른_묶음이_확인돼도_진행_확정_알림이_모두에게_간다() throws Exception {
      long buncheolId = buncheol("C2C", "PAYMENT_COLLECTING", DAY);
      long otherId = fixture.user(false);
      long bundleA = fixture.bundle(buncheolId, otherId, fixture.shippingAddress(otherId), DAY);
      fixture.participation(
          buncheolId,
          fixture.slot(buncheolId, fixture.groupMember(groupId)),
          otherId,
          bundleA,
          "AWAITING_PAYMENT",
          "C2C",
          DAY);
      long bundleB = fixture.bundle(buncheolId, participantId, shippingAddressId, DAY);
      long participationB =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              participantId,
              bundleB,
              "AWAITING_PAYMENT",
              "C2C",
              DAY);

      Future<?> result;
      try (Connection holder = holdBuncheolRow(buncheolId)) {
        result =
            executor.submit(
                () ->
                    participationBundleService.confirmPayment(
                        hostId, bundleB, List.of(participationB)));
        assertThat(awaitBuncheolLockWait(result)).as("경로가 분철 행 잠금을 기다려야 한다").isTrue();
        try (PreparedStatement ps =
            holder.prepareStatement(
                "UPDATE participations SET status = 'CONFIRMED', confirmed_at = UTC_TIMESTAMP()"
                    + " WHERE bundle_id = ?")) {
          ps.setLong(1, bundleA);
          ps.executeUpdate();
        }
        holder.commit();
      }
      result.get(10, TimeUnit.SECONDS);

      assertThat(statusOf("buncheols", buncheolId)).isEqualTo("CONFIRMED");
      assertThat(deliveryBundleIds(buncheolId)).containsExactlyInAnyOrder(bundleA, bundleB);
      assertThat(awaitInboxCount(List.of(otherId, participantId), CONFIRMED_NOTICE))
          .as("진행 확정 안내가 두 참여자 모두에게 1건씩")
          .containsExactly(1, 1);
    }
  }

  @Nested
  @DisplayName("진행 확정 뒤 파기와 운송장 등록")
  class CancelVsTracking {

    @Test
    void 운송장_등록이_먼저_커밋되면_파기는_배송_행을_기다린_뒤_BCH_130_으로_거절된다() throws Exception {
      ConfirmedWithDeliveries confirmed = confirmedWithDeliveries();

      Future<?> result;
      try (Connection tracking = holdTrackingUpdate(confirmed.deliveryIds().get(0))) {
        result = executor.submit(() -> {
          adminBuncheolCommandService.cancel(confirmed.buncheolId());
          return null;
        });
        assertThat(awaitLockWait(result, "deliveries")).as("파기가 배송 행을 기다려야 한다").isTrue();
        tracking.commit();
      }

      assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_HAS_TRACKING);
      assertThat(statusOf("buncheols", confirmed.buncheolId())).isEqualTo("CONFIRMED");
      for (long id : confirmed.participationIds()) {
        assertThat(statusOf("participations", id)).isEqualTo("CONFIRMED");
      }
      assertThat(statusOf("deliveries", confirmed.deliveryIds().get(0))).isEqualTo("SHIPPING");
      assertThat(statusOf("deliveries", confirmed.deliveryIds().get(1))).isEqualTo("SNAPSHOTTED");
    }

    @Test
    void 운송장_등록이_되돌려지면_파기가_배송_행을_기다린_뒤_파기한다() throws Exception {
      ConfirmedWithDeliveries confirmed = confirmedWithDeliveries();

      Future<?> result;
      try (Connection tracking = holdTrackingUpdate(confirmed.deliveryIds().get(0))) {
        result = executor.submit(() -> {
          adminBuncheolCommandService.cancel(confirmed.buncheolId());
          return null;
        });
        assertThat(awaitLockWait(result, "deliveries")).as("파기가 배송 행을 기다려야 한다").isTrue();
        tracking.rollback();
      }
      result.get(10, TimeUnit.SECONDS);

      assertCancelledWithoutDeliveries(confirmed);
    }

    @Test
    void 파기가_먼저_끝나면_운송장_등록은_배송_없음으로_실패하고_아무것도_생기지_않는다() {
      ConfirmedWithDeliveries confirmed = confirmedWithDeliveries();
      adminBuncheolCommandService.cancel(confirmed.buncheolId());

      assertThatThrownBy(
              () ->
                  deliveryService.registerTracking(
                      hostId, confirmed.deliveryIds().get(0), "T1"))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
      assertCancelledWithoutDeliveries(confirmed);
    }

    // 운송장 등록 CAS 가 배송 행을 쥔 채 커밋 전인 상태를 흉내 낸다.
    private Connection holdTrackingUpdate(final long deliveryId) throws SQLException {
      Connection tracking = dataSource.getConnection();
      tracking.setAutoCommit(false);
      try (PreparedStatement ps =
          tracking.prepareStatement(
              "UPDATE deliveries SET status = 'SHIPPING', tracking_number = 'T1',"
                  + " tracking_registered_at = UTC_TIMESTAMP() WHERE id = ?")) {
        ps.setLong(1, deliveryId);
        ps.executeUpdate();
      }
      return tracking;
    }
  }

  @Nested
  @DisplayName("운영진 분철")
  class Staff {

    @Test
    void 운영진_참여_자발_취소는_분철_행을_잠그지_않고_거절된다() throws Exception {
      long buncheolId = buncheol("LEGACY", "RECRUITING", null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId = fixture.bundle(buncheolId, participantId, shippingAddressId, Duration.ofMinutes(10));
      long participationId =
          fixture.participation(
              buncheolId,
              slotId,
              participantId,
              bundleId,
              "AWAITING_PAYMENT",
              "LEGACY",
              Duration.ofMinutes(10));

      try (Connection holder = holdBuncheolRow(buncheolId)) {
        Future<?> result =
            executor.submit(
                () -> {
                  participationService.cancelByParticipant(participantId, participationId);
                  return null;
                });

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.PARTICIPATION_CANCEL_NOT_SUPPORTED);
        holder.rollback();
      }
      assertThat(statusOf("participations", participationId)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 운영진_슬롯_입금_확인은_분철_행을_먼저_기다린다() throws Exception {
      long buncheolId = buncheol("LEGACY", "RECRUITING", null);
      long slotId = fixture.slot(buncheolId, fixture.groupMember(groupId));
      fixture.slot(buncheolId, fixture.groupMember(groupId));
      long bundleId =
          fixture.bundle(buncheolId, participantId, shippingAddressId, Duration.ofMinutes(10));
      long participationId =
          fixture.participation(
              buncheolId,
              slotId,
              participantId,
              bundleId,
              "AWAITING_PAYMENT",
              "LEGACY",
              Duration.ofMinutes(10));

      assertLocksBuncheolFirst(
          buncheolId,
          () -> {
            participationService.confirmPayment(hostId, participationId);
            return null;
          });

      assertThat(statusOf("participations", participationId)).isEqualTo("CONFIRMED");
    }
  }

  private long buncheol(final String flowType, final String status, final Duration paymentDue) {
    return fixture.buncheol(hostId, groupId, flowType, status, DAY, 1, paymentDue);
  }

  /**
   * 다른 연결이 분철 행을 쥔 동안 {@code path} 를 부른다. 그 경로가 분철 행을 기다리는 줄에 서야 하고, 그때 그 분철의 참여·묶음 행은
   * 아무도 잡고 있지 않아야 한다. 쥔 연결을 놓으면 경로가 정상으로 끝나야 한다.
   */
  private void assertLocksBuncheolFirst(final long buncheolId, final Callable<?> path)
      throws Exception {
    assertLocksBuncheolFirst(buncheolId, path, List.of());
  }

  /** {@code deliveryParticipationIds} 가 있으면 그 참여의 배송 행(진행 확정이 만드는 것)도 아직 안 잡았는지 본다. */
  private void assertLocksBuncheolFirst(
      final long buncheolId, final Callable<?> path, final List<Long> deliveryParticipationIds)
      throws Exception {
    Future<?> result;
    try (Connection holder = holdBuncheolRow(buncheolId)) {
      result = executor.submit(path);
      try {
        assertThat(awaitBuncheolLockWait(result))
            .as("경로가 분철 행 잠금을 기다려야 한다")
            .isTrue();
        assertThatCode(() -> lockParticipationsAndBundlesNowait(buncheolId, deliveryParticipationIds))
            .as("분철 행을 기다리는 동안 참여·묶음 행을 잡고 있으면 안 된다")
            .doesNotThrowAnyException();
      } finally {
        holder.rollback();
      }
    }
    result.get(10, TimeUnit.SECONDS);
  }

  private Connection holdBuncheolRow(final long buncheolId) throws SQLException {
    Connection holder = dataSource.getConnection();
    holder.setAutoCommit(false);
    try (PreparedStatement ps =
        holder.prepareStatement("SELECT id FROM buncheols WHERE id = ? FOR UPDATE")) {
      ps.setLong(1, buncheolId);
      ps.executeQuery().close();
    }
    return holder;
  }

  private boolean awaitBuncheolLockWait(final Future<?> result) throws Exception {
    return awaitLockWait(result, "buncheols");
  }

  private boolean awaitLockWait(final Future<?> result, final String table) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      Integer waits =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM performance_schema.data_lock_waits w"
                  + " JOIN performance_schema.data_locks l"
                  + "   ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID"
                  + " WHERE l.OBJECT_SCHEMA = DATABASE() AND l.OBJECT_NAME = ?",
              Integer.class,
              table);
      if (waits != null && waits > 0) {
        return true;
      }
      if (result.isDone()) {
        return false;
      }
      Thread.sleep(20);
    }
    return false;
  }

  private void lockParticipationsAndBundlesNowait(
      final long buncheolId, final List<Long> deliveryParticipationIds) throws SQLException {
    try (Connection probe = dataSource.getConnection()) {
      probe.setAutoCommit(false);
      try {
        for (String sql :
            List.of(
                "SELECT id FROM participations WHERE buncheol_id = ? FOR UPDATE NOWAIT",
                "SELECT id FROM participation_bundles WHERE buncheol_id = ? FOR UPDATE NOWAIT")) {
          try (PreparedStatement ps = probe.prepareStatement(sql)) {
            ps.setLong(1, buncheolId);
            try (ResultSet ignored = ps.executeQuery()) {
              // 잠금을 얻었으면 충분하다.
            }
          }
        }
        if (!deliveryParticipationIds.isEmpty()) {
          String placeholders =
              String.join(",", Collections.nCopies(deliveryParticipationIds.size(), "?"));
          try (PreparedStatement ps =
              probe.prepareStatement(
                  "SELECT id FROM deliveries WHERE participation_id IN ("
                      + placeholders
                      + ") FOR UPDATE NOWAIT")) {
            for (int i = 0; i < deliveryParticipationIds.size(); i++) {
              ps.setLong(i + 1, deliveryParticipationIds.get(i));
            }
            try (ResultSet ignored = ps.executeQuery()) {
              // 잠금을 얻었으면 충분하다.
            }
          }
        }
      } finally {
        probe.rollback();
      }
    }
  }

  private record ConfirmedWithDeliveries(
      long buncheolId, List<Long> participationIds, List<Long> deliveryIds) {}

  /** 진행 확정된 회원 개최 분철. 확인된 묶음 둘(참여자 둘)에 운송장 전 배송이 하나씩 있다. */
  private ConfirmedWithDeliveries confirmedWithDeliveries() {
    long buncheolId =
        fixture.buncheol(
            hostId, groupId, "C2C", "CONFIRMED", Duration.ofHours(-2), 1, Duration.ofHours(-1));
    long otherId = fixture.user(false);
    List<Long> participationIds = new ArrayList<>();
    List<Long> deliveryIds = new ArrayList<>();
    for (long[] person :
        new long[][] {{participantId, shippingAddressId}, {otherId, fixture.shippingAddress(otherId)}}) {
      long bundleId = fixture.bundle(buncheolId, person[0], person[1], Duration.ofHours(-1));
      long participationId =
          fixture.participation(
              buncheolId,
              fixture.slot(buncheolId, fixture.groupMember(groupId)),
              person[0],
              bundleId,
              "CONFIRMED",
              "C2C",
              Duration.ofHours(-1));
      participationIds.add(participationId);
      deliveryIds.add(fixture.delivery(participationId, bundleId));
    }
    return new ConfirmedWithDeliveries(buncheolId, participationIds, deliveryIds);
  }

  private void assertCancelledWithoutDeliveries(final ConfirmedWithDeliveries confirmed) {
    assertThat(statusOf("buncheols", confirmed.buncheolId())).isEqualTo("HOST_CANCELLED");
    for (long id : confirmed.participationIds()) {
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT CONCAT(status, '/', cancel_reason) FROM participations WHERE id = ?",
                  String.class,
                  id))
          .isEqualTo("CANCELLED/BUNCHEOL_CANCELLED");
    }
    assertThat(deliveryBundleIds(confirmed.buncheolId())).isEmpty();
  }

  private List<Long> deliveryBundleIds(final long buncheolId) {
    return jdbcTemplate.queryForList(
        "SELECT d.bundle_id FROM deliveries d"
            + " JOIN participation_bundles pb ON pb.id = d.bundle_id WHERE pb.buncheol_id = ?",
        Long.class,
        buncheolId);
  }

  /** 커밋 뒤 비동기 리스너가 남기는 수신함 기록을 최대 5초 기다려 사람마다 센다. 모두 1 이상이 되면 바로 돌려준다. */
  private List<Integer> awaitInboxCount(final List<Long> recipientIds, final String title)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    List<Integer> counts;
    do {
      counts =
          recipientIds.stream()
              .map(
                  id ->
                      jdbcTemplate.queryForObject(
                          "SELECT COUNT(*) FROM inbox_messages WHERE recipient_id = ? AND title = ?",
                          Integer.class,
                          id,
                          title))
              .toList();
      if (counts.stream().allMatch(count -> count > 0)) {
        return counts;
      }
      Thread.sleep(50);
    } while (System.nanoTime() < deadline);
    return counts;
  }

  private String statusOf(final String table, final long id) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM " + table + " WHERE id = ?", String.class, id);
  }

  /** 입금 단계 시작 CAS 와 그 뒤 처리 — 분철 칸, 살아 있는 참여 전이, 열린 묶음 기한. */
  private void assertPaymentStarted(final long buncheolId) {
    assertThat(statusOf("buncheols", buncheolId)).isEqualTo("PAYMENT_COLLECTING");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payment_due_at IS NOT NULL AND finalized_at IS NOT NULL"
                    + " AND payment_bank IS NOT NULL"
                    + " AND TIMESTAMPDIFF(HOUR, finalized_at, payment_due_at) = 24"
                    + " FROM buncheols WHERE id = ?",
                Boolean.class,
                buncheolId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participations"
                    + " WHERE buncheol_id = ? AND status NOT IN ('AWAITING_PAYMENT', 'CANCELLED')",
                Integer.class,
                buncheolId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participation_bundles"
                    + " WHERE buncheol_id = ? AND closed_at IS NULL AND due_at IS NULL",
                Integer.class,
                buncheolId))
        .isZero();
  }

  private boolean bundleClosed(final long bundleId) {
    return jdbcTemplate.queryForObject(
        "SELECT closed_at IS NOT NULL FROM participation_bundles WHERE id = ?",
        Boolean.class,
        bundleId);
  }
}
