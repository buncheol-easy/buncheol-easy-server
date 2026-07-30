package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaDeliveryRepositoryAdapter 테스트")
class JpaDeliveryRepositoryAdapterTest {

  @Autowired private DeliveryRepository deliveryRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");

  private Long hostId;
  private Long groupId;
  private Long groupMemberId;
  private Long buncheolId;
  private Long buncheolMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_xx");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹");
    groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "테스트 멤버");
    buncheolId = createBuncheol();
    buncheolMemberId = createBuncheolMember(buncheolId);
  }

  @Nested
  @DisplayName("findAllByParticipationIds — participationId IN 조회")
  class FindAllByParticipationIdsTest {

    @Test
    void 빈_입력에는_빈_리스트를_반환한다() {
      List<Delivery> result = deliveryRepository.findAllByParticipationIds(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    void 여러_participationId_에_매핑된_Delivery_를_모두_반환한다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      saveDelivery(participationA, "GS25 잠실점", "트래킹A");
      saveDelivery(participationB, "CU 강남점", null);

      List<Delivery> result =
          deliveryRepository.findAllByParticipationIds(List.of(participationA, participationB));

      assertThat(result)
          .extracting(Delivery::getParticipationId)
          .containsExactlyInAnyOrder(participationA, participationB);
      assertThat(result)
          .filteredOn(d -> d.getParticipationId().equals(participationA))
          .singleElement()
          .satisfies(d -> assertThat(d.getTrackingNumber()).isEqualTo("트래킹A"));
    }

    @Test
    void 매핑되지_않는_participationId_는_결과에서_제외된다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      saveDelivery(participationA, "GS25 잠실점", null);

      List<Delivery> result =
          deliveryRepository.findAllByParticipationIds(List.of(participationA, 99_999L));

      assertThat(result)
          .singleElement()
          .satisfies(d -> assertThat(d.getParticipationId()).isEqualTo(participationA));
    }
  }

  @Nested
  @DisplayName("registerTrackingIfRegistrable — 운송장 등록 CAS")
  class RegisterTrackingCasTest {

    @Test
    void SNAPSHOTTED_상태에서_등록하면_SHIPPING_으로_전이된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", null);

      boolean result =
          deliveryRepository.registerTrackingIfRegistrable(deliveryId, "TRACK123", NOW);

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(found.getTrackingNumber()).isEqualTo("TRACK123");
      assertThat(found.getTrackingRegisteredAt()).isEqualTo(NOW);
    }

    @Test
    void SHIPPING_상태에서_재등록하면_운송장_번호가_교체된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");

      Instant later = NOW.plus(1, ChronoUnit.HOURS);
      boolean result =
          deliveryRepository.registerTrackingIfRegistrable(deliveryId, "TRACK456", later);

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(found.getTrackingNumber()).isEqualTo("TRACK456");
      assertThat(found.getTrackingRegisteredAt()).isEqualTo(later);
    }

    @Test
    void DELIVERED_상태에서는_실패하고_상태가_유지된다() {
      // 웹훅 자동 전이가 먼저 지점 도착을 잡은 경우 — 재등록이 역행시키면 안 된다.
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      forceStatus(deliveryId, DeliveryStatus.DELIVERED);

      boolean result =
          deliveryRepository.registerTrackingIfRegistrable(deliveryId, "TRACK456", NOW);

      assertThat(result).isFalse();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
      assertThat(found.getTrackingNumber()).isEqualTo("TRACK123");
    }

    @Test
    void RECEIVED_상태에서는_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      forceStatus(deliveryId, DeliveryStatus.RECEIVED);

      boolean result =
          deliveryRepository.registerTrackingIfRegistrable(deliveryId, "TRACK456", NOW);

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
    }
  }

  @Nested
  @DisplayName("confirmReceiptIfActive — 수령 확인 CAS")
  class ConfirmReceiptCasTest {

    @Test
    void SHIPPING_상태에서_수령_확인하면_RECEIVED_로_전이된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");

      boolean result = deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
      assertThat(found.getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    void DELIVERED_상태에서_수령_확인하면_RECEIVED_로_전이된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      forceStatus(deliveryId, DeliveryStatus.DELIVERED);

      boolean result = deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      assertThat(result).isTrue();
      assertThat(findFresh(deliveryId).getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
    }

    @Test
    void SNAPSHOTTED_상태에서는_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", null);

      boolean result = deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      assertThat(result).isFalse();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.SNAPSHOTTED);
      assertThat(found.getReceivedAt()).isNull();
    }

    @Test
    void 이미_RECEIVED_면_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      boolean result =
          deliveryRepository.confirmReceiptIfActive(deliveryId, NOW.plus(1, ChronoUnit.HOURS));

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getReceivedAt()).isEqualTo(NOW);
    }
  }

  @Nested
  @DisplayName("markDeliveredIfShipping — 지점 도착 감지 CAS")
  class MarkDeliveredCasTest {

    @Test
    void SHIPPING_상태에서_도착_감지시_DELIVERED_로_전이된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      Instant eventTime = NOW.plus(1, ChronoUnit.DAYS);

      boolean result =
          deliveryRepository.markDeliveredIfShipping(
              deliveryId, eventTime, eventTime.plusSeconds(60));

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
      assertThat(found.getDeliveredAt()).isEqualTo(eventTime);
    }

    @Test
    void 이미_DELIVERED_면_실패한다_콜백_중복_멱등() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.markDeliveredIfShipping(deliveryId, NOW, NOW);

      boolean result =
          deliveryRepository.markDeliveredIfShipping(
              deliveryId, NOW.plus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS));

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void RECEIVED_상태에서는_실패하고_역행하지_않는다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      boolean result = deliveryRepository.markDeliveredIfShipping(deliveryId, NOW, NOW);

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
    }
  }

  @Nested
  @DisplayName("markReceivedIfDelivered / markReceivedIfShipping — 고객 수령 감지 CAS")
  class MarkReceivedCasTest {

    @Test
    void DELIVERED_상태에서_수령_감지시_RECEIVED_로_전이되고_deliveredAt_은_유지된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.markDeliveredIfShipping(deliveryId, NOW, NOW);
      Instant eventTime = NOW.plus(1, ChronoUnit.DAYS);

      boolean result =
          deliveryRepository.markReceivedIfDelivered(deliveryId, eventTime, eventTime);

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
      assertThat(found.getReceivedAt()).isEqualTo(eventTime);
      assertThat(found.getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void SHIPPING_상태에서는_markReceivedIfDelivered_가_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");

      boolean result = deliveryRepository.markReceivedIfDelivered(deliveryId, NOW, NOW);

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    void 도착_감지를_놓친_직행_전이는_deliveredAt_도_함께_채운다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      Instant eventTime = NOW.plus(1, ChronoUnit.DAYS);

      boolean result = deliveryRepository.markReceivedIfShipping(deliveryId, eventTime, eventTime);

      assertThat(result).isTrue();
      Delivery found = findFresh(deliveryId);
      assertThat(found.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
      assertThat(found.getDeliveredAt()).isEqualTo(eventTime);
      assertThat(found.getReceivedAt()).isEqualTo(eventTime);
    }

    @Test
    void 참여자가_먼저_수령확인한_배송은_직행_전이가_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      boolean result =
          deliveryRepository.markReceivedIfShipping(
              deliveryId, NOW.plus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS));

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getReceivedAt()).isEqualTo(NOW);
    }
  }

  @Nested
  @DisplayName("markPickupReminderSent — 미수령 독촉 발송 마킹 CAS")
  class MarkPickupReminderSentTest {

    @Test
    void DELIVERED_이고_미발송이면_마킹에_성공한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.markDeliveredIfShipping(deliveryId, NOW, NOW);

      boolean result = deliveryRepository.markPickupReminderSent(deliveryId, NOW);

      assertThat(result).isTrue();
      assertThat(findFresh(deliveryId).getPickupReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_발송했으면_실패한다_중복_발송_차단() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.markDeliveredIfShipping(deliveryId, NOW, NOW);
      deliveryRepository.markPickupReminderSent(deliveryId, NOW);

      boolean result =
          deliveryRepository.markPickupReminderSent(deliveryId, NOW.plus(1, ChronoUnit.HOURS));

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getPickupReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    void 그_사이_수령된_배송은_마킹에_실패한다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK123");
      deliveryRepository.confirmReceiptIfActive(deliveryId, NOW);

      boolean result = deliveryRepository.markPickupReminderSent(deliveryId, NOW);

      assertThat(result).isFalse();
      assertThat(findFresh(deliveryId).getPickupReminderSentAt()).isNull();
    }
  }

  @Nested
  @DisplayName("findPickupReminderTargets — 미수령 독촉 대상 조회")
  class FindPickupReminderTargetsTest {

    @Test
    void 도착이_기준_시각_이전인_미독촉_배송만_도착_오래된_순으로_반환한다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      Long participationC = createConfirmedParticipation("fanC", 70_000L);
      Long participationD = createConfirmedParticipation("fanD", 60_000L);
      // 기준 이전 도착 2건 (도착 순서 역순으로 저장해 정렬 검증)
      Long lateArrival = saveDelivery(participationA, "GS25 잠실점", "TRACK-1");
      deliveryRepository.markDeliveredIfShipping(lateArrival, NOW.minus(2, ChronoUnit.DAYS), NOW);
      Long earlyArrival = saveDelivery(participationB, "GS25 강남점", "TRACK-2");
      deliveryRepository.markDeliveredIfShipping(earlyArrival, NOW.minus(3, ChronoUnit.DAYS), NOW);
      // 기준 이후 도착 — 제외
      Long recentArrival = saveDelivery(participationC, "GS25 송파점", "TRACK-3");
      deliveryRepository.markDeliveredIfShipping(recentArrival, NOW, NOW);
      // 아직 배송중 — 제외
      saveDelivery(participationD, "GS25 마포점", "TRACK-4");

      List<Delivery> result =
          deliveryRepository.findPickupReminderTargets(NOW.minus(1, ChronoUnit.DAYS), 100);

      assertThat(result).extracting(Delivery::getId).containsExactly(earlyArrival, lateArrival);
    }

    @Test
    void 이미_독촉한_배송은_제외된다() {
      Long participationId = createConfirmedParticipation("fanA", 90_000L);
      Long deliveryId = saveDelivery(participationId, "GS25 잠실점", "TRACK-1");
      deliveryRepository.markDeliveredIfShipping(deliveryId, NOW.minus(2, ChronoUnit.DAYS), NOW);
      deliveryRepository.markPickupReminderSent(deliveryId, NOW);

      List<Delivery> result =
          deliveryRepository.findPickupReminderTargets(NOW.minus(1, ChronoUnit.DAYS), 100);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findTrackedParcels — 추적 중 운송장 중복 제거 조회")
  class FindTrackedParcelsTest {

    @Test
    void 같은_운송장_다건_배송은_한_건으로_중복_제거된다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      saveDelivery(participationA, "GS25 잠실점", "TRACK123");
      saveDelivery(participationB, "GS25 강남점", "TRACK123");

      List<TrackedParcel> result = deliveryRepository.findTrackedParcels(100);

      assertThat(result)
          .containsExactly(new TrackedParcel(ShippingMethod.GS25_HALF, "TRACK123"));
    }

    @Test
    void 배치_상한만큼만_조회한다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      Long participationC = createConfirmedParticipation("fanC", 70_000L);
      saveDelivery(participationA, "GS25 잠실점", "TRACK-1");
      saveDelivery(participationB, "GS25 강남점", "TRACK-2");
      saveDelivery(participationC, "GS25 송파점", "TRACK-3");

      List<TrackedParcel> result = deliveryRepository.findTrackedParcels(2);

      assertThat(result).hasSize(2);
    }

    @Test
    void 운송장_미등록과_수령완료_배송은_제외된다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      Long participationC = createConfirmedParticipation("fanC", 70_000L);
      saveDelivery(participationA, "GS25 잠실점", null);
      Long receivedId = saveDelivery(participationB, "GS25 강남점", "TRACK-DONE");
      deliveryRepository.confirmReceiptIfActive(receivedId, NOW);
      Long deliveredId = saveDelivery(participationC, "GS25 송파점", "TRACK-ARRIVED");
      deliveryRepository.markDeliveredIfShipping(deliveredId, NOW, NOW);

      List<TrackedParcel> result = deliveryRepository.findTrackedParcels(100);

      // DELIVERED(지점 도착)는 아직 수령 감지가 남아 추적을 계속한다.
      assertThat(result)
          .containsExactly(new TrackedParcel(ShippingMethod.GS25_HALF, "TRACK-ARRIVED"));
    }
  }

  @Nested
  @DisplayName("findAllByTrackingNumber — 운송장·배송방식·상태 조회")
  class FindAllByTrackingNumberTest {

    @Test
    void 같은_운송장에_매핑된_추적_중_배송을_전부_반환한다() {
      // 관리자 벌크 등록은 한 운송장을 여러 배송에 매핑한다 — 콜백 한 건이 전부 전이시켜야 한다.
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      Long deliveryA = saveDelivery(participationA, "GS25 잠실점", "TRACK123");
      Long deliveryB = saveDelivery(participationB, "GS25 강남점", "TRACK123");

      List<Delivery> result =
          deliveryRepository.findAllByTrackingNumber(
              "TRACK123",
              ShippingMethod.GS25_HALF,
              Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED));

      assertThat(result).extracting(Delivery::getId).containsExactlyInAnyOrder(deliveryA, deliveryB);
    }

    @Test
    void 수령완료된_배송과_다른_배송방식은_제외된다() {
      Long participationA = createConfirmedParticipation("fanA", 90_000L);
      Long participationB = createConfirmedParticipation("fanB", 80_000L);
      Long receivedId = saveDelivery(participationA, "GS25 잠실점", "TRACK123");
      deliveryRepository.confirmReceiptIfActive(receivedId, NOW);
      Long shippingId = saveDelivery(participationB, "GS25 강남점", "TRACK123");

      List<Delivery> result =
          deliveryRepository.findAllByTrackingNumber(
              "TRACK123",
              ShippingMethod.GS25_HALF,
              Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED));

      assertThat(result).extracting(Delivery::getId).containsExactly(shippingId);

      List<Delivery> otherMethod =
          deliveryRepository.findAllByTrackingNumber(
              "TRACK123",
              ShippingMethod.CU_HALF,
              Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED));

      assertThat(otherMethod).isEmpty();
    }
  }

  private Long createBuncheol() {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 1, 3000, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    return buncheol.getId();
  }

  private Long createBuncheolMember(final Long buncheolId) {
    return createBuncheolMember(buncheolId, groupMemberId);
  }

  private Long createBuncheolMember(final Long buncheolId, final Long memberId) {
    BuncheolMember member = BuncheolMember.create(buncheolId, memberId, 30_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    em.flush();
    return member.getId();
  }

  /**
   * 서로 다른 참여자/멤버 슬롯/배송지로 CONFIRMED 참여를 만들고 그 id 를 반환한다. 한 멤버 슬롯엔 활성 참여가 1건만 가능(active_member_id
   * UNIQUE)하므로 참여마다 별도 멤버 슬롯을 생성한다.
   */
  private Long createConfirmedParticipation(final String userSuffix, final long amount) {
    Long participantId = TestUserFixture.insertUser(jdbcTemplate, userSuffix);
    Long memberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, userSuffix + "_멤버");
    Long slotId = createBuncheolMember(buncheolId, memberId);
    Long shippingAddressId = insertShippingAddress(participantId, userSuffix + "_매장");
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, amount, refund_bank, refund_account, refund_holder,"
            + " due_at, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        slotId,
        participantId,
        shippingAddressId,
        amount,
        "국민",
        "12345678",
        "홍길동",
        Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)),
        ParticipationStatus.CONFIRMED.name());
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE shipping_address_id = ?", Long.class, shippingAddressId);
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

  private Long saveDelivery(
      final Long participationId, final String storeName, final String trackingNumber) {
    Delivery delivery =
        Delivery.createSnapshot(
            participationId, ShippingMethod.GS25_HALF, storeName, "수령인", "010-1234-5678");
    deliveryRepository.save(delivery);
    em.flush();
    if (trackingNumber != null) {
      deliveryRepository.registerTrackingIfRegistrable(delivery.getId(), trackingNumber, NOW);
    }
    return delivery.getId();
  }

  private Delivery findFresh(final Long deliveryId) {
    em.flush();
    em.clear();
    return deliveryRepository.findById(deliveryId).orElseThrow();
  }

  /** CAS 를 우회해 상태를 강제 세팅한다 (DELIVERED 등 아직 도달 경로가 없는 상태의 픽스처용). */
  private void forceStatus(final Long deliveryId, final DeliveryStatus status) {
    jdbcTemplate.update("UPDATE deliveries SET status = ? WHERE id = ?", status.name(), deliveryId);
    em.clear();
  }
}
