package buncheoleasy.delivery.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

  Delivery save(Delivery delivery);

  /**
   * 운송장 등록 CAS (SNAPSHOTTED → SHIPPING, SHIPPING 재등록은 번호 last-write-wins). 성공 여부를 반환하며 호출 측
   * {@code @Transactional} 필수.
   */
  boolean registerTrackingIfRegistrable(Long id, String trackingNumber, Instant now);

  /** 수령 확인 CAS (SHIPPING·DELIVERED → RECEIVED). 성공 여부를 반환하며 호출 측 {@code @Transactional} 필수. */
  boolean confirmReceiptIfActive(Long id, Instant now);

  Optional<Delivery> findById(Long id);

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIds(List<Long> participationIds);

  /** 취소된 참여들의 배송 스냅샷을 일괄 삭제한다 (분철 취소 cascade 시 고아 스냅샷 정리). 호출 측 {@code @Transactional} 필수. */
  void deleteByParticipationIds(List<Long> participationIds);
}
