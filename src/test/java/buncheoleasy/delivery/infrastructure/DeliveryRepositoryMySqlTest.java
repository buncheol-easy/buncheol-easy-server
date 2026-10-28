package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DeliveryRepository (MySQL)")
class DeliveryRepositoryMySqlTest extends MySqlContainerTestSupport {

  @Autowired private DeliveryRepository deliveryRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DataSource dataSource;

  // 진행 확정 뒤 운영진 파기가 「운송장 전」을 판정하는 읽기 — 읽은 배송 행을 쥐고 있어야 운송장 등록 CAS 와 한쪽만 반영된다.
  @Test
  void 참여_id_로_배송을_잠가_읽고_다른_참여의_배송은_건드리지_않는다() {
    MySqlFixture fixture = new MySqlFixture(jdbcTemplate);
    long hostId = fixture.user(false);
    long groupId = fixture.group();
    long participantId = fixture.user(false);
    long addressId = fixture.shippingAddress(participantId);
    long buncheolId =
        fixture.buncheol(hostId, groupId, "C2C", "CONFIRMED", Duration.ofHours(-1), 1, null);
    long bundleId = fixture.bundle(buncheolId, participantId, addressId, null);
    long otherBundleId = fixture.bundle(buncheolId, participantId, addressId, null);
    long target = confirmed(fixture, buncheolId, groupId, participantId, bundleId);
    long other = confirmed(fixture, buncheolId, groupId, participantId, otherBundleId);
    long targetDelivery = fixture.delivery(target, bundleId);
    long otherDelivery = fixture.delivery(other, otherBundleId);

    List<Long> lockedIds =
        transactionTemplate.execute(
            status -> {
              List<Long> ids =
                  deliveryRepository.findAllByParticipationIdsForUpdate(List.of(target)).stream()
                      .map(Delivery::getId)
                      .toList();
              assertThatThrownBy(() -> lockNowait(targetDelivery))
                  .as("읽은 배송 행은 잠겨 있어야 한다")
                  .isInstanceOf(SQLException.class);
              assertThat(lockNowaitSucceeds(otherDelivery)).as("다른 참여의 배송은 잠그지 않는다").isTrue();
              return ids;
            });

    assertThat(lockedIds).containsExactly(targetDelivery);
    List<Delivery> none =
        transactionTemplate.execute(
            status -> deliveryRepository.findAllByParticipationIdsForUpdate(List.of()));
    assertThat(none).isEmpty();
  }

  private static long confirmed(
      final MySqlFixture fixture,
      final long buncheolId,
      final long groupId,
      final long participantId,
      final long bundleId) {
    return fixture.participation(
        buncheolId,
        fixture.slot(buncheolId, fixture.groupMember(groupId)),
        participantId,
        bundleId,
        "CONFIRMED",
        "C2C",
        null);
  }

  private void lockNowait(final long deliveryId) throws SQLException {
    try (Connection probe = dataSource.getConnection()) {
      probe.setAutoCommit(false);
      try (PreparedStatement ps =
          probe.prepareStatement("SELECT id FROM deliveries WHERE id = ? FOR UPDATE NOWAIT")) {
        ps.setLong(1, deliveryId);
        ps.executeQuery().close();
      } finally {
        probe.rollback();
      }
    }
  }

  private boolean lockNowaitSucceeds(final long deliveryId) {
    try {
      lockNowait(deliveryId);
      return true;
    } catch (SQLException e) {
      return false;
    }
  }
}
