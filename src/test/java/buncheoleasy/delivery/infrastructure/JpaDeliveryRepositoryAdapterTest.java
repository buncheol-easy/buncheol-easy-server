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
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

  private void saveDelivery(
      final Long participationId, final String storeName, final String trackingNumber) {
    Delivery delivery =
        Delivery.createSnapshot(
            participationId, ShippingMethod.GS25_HALF, storeName, "수령인", "010-1234-5678");
    if (trackingNumber != null) {
      delivery.registerTracking(trackingNumber, Instant.now());
    }
    deliveryRepository.save(delivery);
    em.flush();
  }
}
