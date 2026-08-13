package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.delivery.domain.DeliveryStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  /**
   * 아직 끝나지 않은 참여가 있는지 (회원탈퇴 가드). 입금 확인 중이거나, 입금확인됐지만 배송이 끝나지 않았거나
   * (배송 스냅샷이 없으면 미종료로 본다), 배송비 환급 신청이 검수 대기 중이면 끝나지 않은 것으로 판정한다.
   */
  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "WHERE p.participantId = :participantId "
          + "AND (p.status IN :pendingStatuses "
          + "  OR (p.status = :confirmedStatus "
          + "    AND (p.paybackStatus = :requestedPaybackStatus "
          + "      OR NOT EXISTS ("
          + "        SELECT d FROM Delivery d "
          + "        WHERE d.participationId = p.id AND d.status IN :finishedDeliveryStatuses))))")
  boolean existsUnfinishedByParticipantId(
      @Param("participantId") Long participantId,
      @Param("pendingStatuses") Collection<ParticipationStatus> pendingStatuses,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("requestedPaybackStatus") PaybackStatus requestedPaybackStatus,
      @Param("finishedDeliveryStatuses") Set<DeliveryStatus> finishedDeliveryStatuses);

  boolean existsByBuncheolIdAndParticipantIdAndStatusIn(
      Long buncheolId, Long participantId, Collection<ParticipationStatus> statuses);

  boolean existsByShippingAddressIdAndStatusIn(
      Long shippingAddressId, Collection<ParticipationStatus> statuses);

  @Query(
      "SELECT new buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount("
          + "p.buncheolId, COUNT(p)) "
          + "FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status IN :activeStatuses "
          + "GROUP BY p.buncheolId")
  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses);

  @Query(
      "SELECT new buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount("
          + "p.buncheolId, COUNT(p)) "
          + "FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status = :status "
          + "GROUP BY p.buncheolId")
  List<BuncheolConfirmedParticipationCount> countByBuncheolIdsAndStatus(
      @Param("buncheolIds") List<Long> buncheolIds, @Param("status") ParticipationStatus status);

  @Query(
      "SELECT p FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :statuses "
          + "ORDER BY p.createdAt ASC, p.id ASC")
  List<Participation> findByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("statuses") Collection<ParticipationStatus> statuses);

  /** 활성 참여의 참여자 id 잠금 조회(current read, 행당 1건) — 정원 충족 판정용. 집계 + FOR UPDATE 는 H2 미지원이라 프로젝션으로 세운다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT p.participantId FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :statuses")
  List<Long> findParticipantIdsByBuncheolIdAndStatusInForUpdate(
      @Param("buncheolId") Long buncheolId,
      @Param("statuses") Collection<ParticipationStatus> statuses);

  @Query(
      "SELECT p.buncheolMemberId FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status IN :activeStatuses")
  List<Long> findActiveBuncheolMemberIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses);

  List<Participation> findByBuncheolIdAndStatusOrderByCreatedAtAscIdAsc(
      Long buncheolId, ParticipationStatus status);

  List<Participation> findByBuncheolIdAndStatusAndCancelReason(
      Long buncheolId, ParticipationStatus status, ParticipationCancelReason cancelReason);

  int countByBuncheolIdAndStatus(Long buncheolId, ParticipationStatus status);

  List<Participation> findByStatusAndDueAtLessThanEqualOrderByDueAtAsc(
      ParticipationStatus status, Instant dueAt, Limit limit);

  boolean existsByPaybackTweetUrlAndIdNot(String paybackTweetUrl, Long id);

  /** AWAITING_PAYMENT 이고 입금 기한 내일 때만 CONFIRMED 로 전이 (호스트 수동 입금확인 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :confirmedStatus, p.confirmedAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :awaitingStatus AND p.dueAt >= :now")
  int confirmPaymentIfAwaiting(
      @Param("id") Long id,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("now") Instant now);

  /** AWAITING_PAYMENT 이고 입금 기한(dueAt)이 지났을 때만 지정 사유로 CANCELLED 로 전이 (입금 만료 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :awaitingStatus AND p.dueAt <= :now")
  int cancelIfAwaitingAndOverdue(
      @Param("id") Long id,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("reason") ParticipationCancelReason reason,
      @Param("now") Instant now);

  /** 분철의 특정 상태 참여를 모두 지정 사유로 CANCELLED 로 일괄 전이 (분철 취소 cascade). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :targetStatuses")
  int cancelByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("targetStatuses") Collection<ParticipationStatus> targetStatuses,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("reason") ParticipationCancelReason reason,
      @Param("now") Instant now);

  /** 환급 신청이 REQUESTED 일 때만 COMPLETED 로 전이 (운영진 입금완료 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.paybackStatus = :completedStatus, p.paybackCompletedAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.paybackStatus = :requestedStatus")
  int completePaybackIfRequested(
      @Param("id") Long id,
      @Param("requestedStatus") PaybackStatus requestedStatus,
      @Param("completedStatus") PaybackStatus completedStatus,
      @Param("now") Instant now);

  /** 환급 신청이 REQUESTED 일 때만 사유와 함께 REJECTED 로 전이 (운영진 반려 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.paybackStatus = :rejectedStatus, p.paybackRejectReason = :reason, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.paybackStatus = :requestedStatus")
  int rejectPaybackIfRequested(
      @Param("id") Long id,
      @Param("requestedStatus") PaybackStatus requestedStatus,
      @Param("rejectedStatus") PaybackStatus rejectedStatus,
      @Param("reason") String reason,
      @Param("now") Instant now);

  // --- C2C 플로우 CAS (docs/46 §4) ---

  /**
   * C2C "보냈어요" 마킹 CAS (AWAITING_PAYMENT → PAYMENT_SENT). 기한 경과 검사 없음 — 기한 직전 입금 보호가 목적이며, 만료
   * 스케줄러 CAS 와 경합하면 정확히 한쪽만 성공한다 (docs/46 §4.2).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :sentStatus, p.paymentSentAt = :now, "
          + "    p.paymentRejectedAt = NULL, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :awaitingStatus")
  int markPaymentSentIfAwaiting(
      @Param("id") Long id,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("sentStatus") ParticipationStatus sentStatus,
      @Param("now") Instant now);

  /**
   * C2C 마킹 해제 CAS (PAYMENT_SENT → AWAITING_PAYMENT 복귀). 참여자 철회(기한 유지)와 개최자 반려(기한 연장 — docs/46
   * §4.5)가 공용하며, {@code paymentSentAt} 은 분쟁 증거로 보존한다.
   *
   * <p>{@code rejectedAt} 으로 두 경로를 구분한다 (docs/53 Q-03) — 개최자 반려는 현재 시각, 참여자 셀프 철회는 {@code null}. 응답에
   * 그대로 실어 FE 가 "입금 확인 안 됨 · 재확인 필요" 를 띄운다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :awaitingStatus, p.dueAt = :dueAt, "
          + "    p.paymentRejectedAt = :rejectedAt, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :sentStatus")
  int revertPaymentSentIfSent(
      @Param("id") Long id,
      @Param("sentStatus") ParticipationStatus sentStatus,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("dueAt") Instant dueAt,
      @Param("rejectedAt") Instant rejectedAt,
      @Param("now") Instant now);

  /** C2C 참여자 자발 취소 CAS — 신청(APPLIED)·입금 대기(AWAITING_PAYMENT)에서만 (docs/46 §5 구간 ①·②). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status IN :cancellableStatuses")
  int cancelIfStatusIn(
      @Param("id") Long id,
      @Param("cancellableStatuses") Collection<ParticipationStatus> cancellableStatuses,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("reason") ParticipationCancelReason reason,
      @Param("now") Instant now);

  /**
   * C2C 개최자 수동 입금확인 CAS — 입금 대기(AWAITING_PAYMENT·PAYMENT_SENT)면 기한 경과와 무관하게 CONFIRMED 로 전이한다
   * (개최자 확인이 늦어도 유효 — docs/46 §3-6).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :confirmedStatus, p.confirmedAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status IN :payableStatuses")
  int confirmPaymentIfPayable(
      @Param("id") Long id,
      @Param("payableStatuses") Collection<ParticipationStatus> payableStatuses,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("now") Instant now);

  /** C2C 성사 확정: 분철의 APPLIED 전건을 일괄 입금 기한과 함께 AWAITING_PAYMENT 로 전이 (docs/46 §4.1). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :awaitingStatus, p.dueAt = :dueAt, p.updatedAt = :now "
          + "WHERE p.buncheolId = :buncheolId AND p.status = :appliedStatus")
  int startPaymentCollecting(
      @Param("buncheolId") Long buncheolId,
      @Param("appliedStatus") ParticipationStatus appliedStatus,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("dueAt") Instant dueAt,
      @Param("now") Instant now);
}
