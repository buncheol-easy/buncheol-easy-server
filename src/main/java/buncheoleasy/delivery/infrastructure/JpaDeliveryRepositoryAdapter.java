package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryRepositoryAdapter implements DeliveryRepository {

  private final JpaDeliveryRepository jpaDeliveryRepository;

  @Override
  public Delivery save(final Delivery delivery) {
    return jpaDeliveryRepository.save(delivery);
  }

  @Override
  public boolean registerTrackingIfRegistrable(
      final Long id, final String trackingNumber, final Instant now) {
    int updated =
        jpaDeliveryRepository.registerTrackingIfRegistrable(
            id, trackingNumber, DeliveryStatus.SNAPSHOTTED, DeliveryStatus.SHIPPING, now);
    return updated > 0;
  }

  @Override
  public boolean confirmReceiptIfActive(final Long id, final Instant now) {
    int updated =
        jpaDeliveryRepository.confirmReceiptIfActive(
            id,
            DeliveryStatus.SHIPPING,
            DeliveryStatus.DELIVERED,
            DeliveryStatus.RECEIVED,
            now);
    return updated > 0;
  }

  @Override
  public boolean markDeliveredIfShipping(
      final Long id, final Instant eventTime, final Instant now) {
    int updated =
        jpaDeliveryRepository.markDeliveredIfShipping(
            id, DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED, eventTime, now);
    return updated > 0;
  }

  @Override
  public boolean markReceivedIfDelivered(
      final Long id, final Instant eventTime, final Instant now) {
    int updated =
        jpaDeliveryRepository.markReceivedIfDelivered(
            id, DeliveryStatus.DELIVERED, DeliveryStatus.RECEIVED, eventTime, now);
    return updated > 0;
  }

  @Override
  public boolean markReceivedIfShipping(
      final Long id, final Instant eventTime, final Instant now) {
    int updated =
        jpaDeliveryRepository.markReceivedIfShipping(
            id, DeliveryStatus.SHIPPING, DeliveryStatus.RECEIVED, eventTime, now);
    return updated > 0;
  }

  @Override
  public List<Delivery> findAllByTrackingNumber(
      final String trackingNumber,
      final ShippingMethod shippingMethod,
      final Collection<DeliveryStatus> statuses) {
    return jpaDeliveryRepository.findAllByTrackingNumberAndShippingMethodAndStatusIn(
        trackingNumber, shippingMethod, statuses);
  }

  @Override
  public List<TrackedParcel> findTrackedParcels(final Instant registeredAfter, final int limit) {
    return jpaDeliveryRepository.findTrackedParcels(
        Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED),
        registeredAfter,
        Limit.of(limit));
  }

  @Override
  public List<Delivery> findPickupReminderTargets(final Instant threshold, final int limit) {
    return jpaDeliveryRepository
        .findByStatusAndDeliveredAtLessThanEqualAndPickupReminderSentAtIsNullOrderByDeliveredAtAsc(
            DeliveryStatus.DELIVERED, threshold, Limit.of(limit));
  }

  @Override
  public boolean markPickupReminderSent(final Long id, final Instant now) {
    int updated =
        jpaDeliveryRepository.markPickupReminderSentIfDue(id, DeliveryStatus.DELIVERED, now);
    return updated > 0;
  }

  @Override
  public Optional<Delivery> findById(final Long id) {
    return jpaDeliveryRepository.findById(id);
  }

  @Override
  public Optional<Delivery> findByBundleId(final Long bundleId) {
    // 묶음이 없는 참여(배포선 창에서 생긴 행)는 배송도 찾을 수 없다 — null 을 키로 넘기면 IS NULL 조회가 되어
    // 남의 배송이 걸린다. 여기서 끊는다.
    if (bundleId == null) {
      return Optional.empty();
    }
    return jpaDeliveryRepository.findFirstByBundleIdOrderByIdAsc(bundleId);
  }

  @Override
  public List<Delivery> findAllByBundleIds(final List<Long> bundleIds) {
    List<Long> ids = bundleIds.stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    return jpaDeliveryRepository.findAllByBundleIdInOrderByIdAsc(ids);
  }

  @Override
  public void deleteByParticipationIds(final List<Long> participationIds) {
    if (participationIds.isEmpty()) {
      return;
    }
    jpaDeliveryRepository.deleteByParticipationIdIn(participationIds);
  }
}
