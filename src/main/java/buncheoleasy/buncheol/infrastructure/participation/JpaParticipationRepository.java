package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.FlowType;
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
   *
   * <p>배송은 <b>묶음</b>으로 찾는다 — 택배 1개 = 묶음 1개라 다슬롯 묶음의 두 번째 슬롯에는 자기 배송 행이
   * 없다. 참여 id 로 찾으면 그 슬롯이 영원히 "배송 미종료" 로 남아 탈퇴가 막힌다.
   * <p>🔴 <b>{@code p.bundleId} 가 NULL 이면 이 조건은 어느 배송과도 매칭되지 않는다</b> — 그 참여는 영구히
   * "배송 미종료" 로 남아 탈퇴가 막힌다. <b>지금 그런 행은 없다</b>: 2026-08-31 실측으로 {@code bundle_id IS
   * NULL} 인 참여가 prod 0/47 · staging 0/104 이고, 참여를 만드는 세 경로가 모두 같은 트랜잭션에서
   * {@code ParticipationBundleDomainService#attach} 를 부르며 그 연결 CAS 가 실패하면 예외로 전체 롤백된다
   * — 즉 <b>묶음 없는 참여를 만들 수 있는 코드 경로가 없다</b>. 참여 id 폴백을 남기지 않은 근거가 이것이다.
   * P4 가 {@code bundle_id} 를 NOT NULL 로 조이면 이 전제가 스키마로 굳는다.
   */
  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "WHERE p.participantId = :participantId "
          + "AND (p.status IN :pendingStatuses "
          + "  OR (p.status = :confirmedStatus "
          + "    AND (p.paybackStatus = :requestedPaybackStatus "
          + "      OR NOT EXISTS ("
          + "        SELECT d FROM Delivery d "
          + "        WHERE d.bundleId = p.bundleId AND d.status IN :finishedDeliveryStatuses))))")
  boolean existsUnfinishedByParticipantId(
      @Param("participantId") Long participantId,
      @Param("pendingStatuses") Collection<ParticipationStatus> pendingStatuses,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("requestedPaybackStatus") PaybackStatus requestedPaybackStatus,
      @Param("finishedDeliveryStatuses") Set<DeliveryStatus> finishedDeliveryStatuses);

  boolean existsByBuncheolIdAndParticipantIdAndStatusIn(
      Long buncheolId, Long participantId, Collection<ParticipationStatus> statuses);

  /**
   * 배송지 삭제 가드 <b>정본 항</b> — 이 배송지를 쓰는 <b>묶음</b>에 활성 슬롯이 있는가.
   *
   * <p>🔴 <b>이 항만으로는 부족하다.</b> 어댑터가 사본 항({@link #existsByShippingAddressIdAndStatusIn})과 OR 로 합성한다 —
   * 두 질의를 나눈 이유는 <b>각각 배송지 FK 인덱스로 진입</b>시키기 위함이다. 한 JPQL 안에서 OR 로 묶으면 옵티마이저가
   * {@code Participation} 부터 읽어 활성 참여 전건을 훑는다(이 가드는 사용자 배송지 삭제 경로마다 돈다).
   *
   * <p>판정 축은 묶음의 {@code closed_at} 이 아니라 <b>슬롯 상태</b>를 그대로 쓴다. 묶음 닫기가 늦거나 실패한 「시체 묶음」에
   * 가드를 매달면 실제로는 끝난 배송지를 영원히 못 지운다.
   */
  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "JOIN ParticipationBundle b ON b.id = p.bundleId "
          + "WHERE b.shippingAddressId = :shippingAddressId AND p.status IN :statuses")
  boolean existsActiveByBundleShippingAddress(
      @Param("shippingAddressId") Long shippingAddressId,
      @Param("statuses") Collection<ParticipationStatus> statuses);

  /**
   * 배송지 삭제 가드 <b>사본 항</b> — 참여 행에 남은 옛 배송지 값으로 찾는다.
   *
   * <p>🔴 <b>단독으로 쓰면 안 된다.</b> 참여 INSERT 에서 배송지를 뺀 뒤 신규 행의 이 칸은 항상 NULL 이라, 이것만으로는
   * <b>전 건에 false 를 내는 무력한 통과 장치</b>가 된다. 그러면 배송 대기 중인 배송지가 사용자 손으로 지워지고,
   * {@code ON DELETE SET NULL} 이 <b>정본까지</b> NULL 로 만든다 — 두 칸 모두 {@code updatable = false} 라 코드로 복구가
   * 불가능하다. 반드시 위 정본 항과 OR 로 합성해 쓴다.
   *
   * <p>⚠️ <b>지우지 말 것.</b> 가드는 비대칭이다 — 과탐(삭제를 막음)은 재시도로 끝나지만 미탐은 비가역이다. 신규 행에서는
   * 절대 매칭되지 않으므로 과탐이 늘지도 않는다. 정본과 사본이 어긋난 옛 행만 fail-closed 로 덮는 항이고,
   * P4 에서 참여 컬럼을 DROP 할 때 함께 지운다.
   */
  boolean existsByShippingAddressIdAndStatusIn(
      Long shippingAddressId, Collection<ParticipationStatus> statuses);

  boolean existsByBuncheolMemberIdAndStatusIn(
      Long buncheolMemberId, Collection<ParticipationStatus> statuses);

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
  List<BuncheolConfirmedParticipationCount> countConfirmedByBuncheolIds(
      @Param("buncheolIds") List<Long> buncheolIds, @Param("status") ParticipationStatus status);

  @Query(
      "SELECT p FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :statuses "
          + "ORDER BY p.createdAt ASC, p.id ASC")
  List<Participation> findByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("statuses") Collection<ParticipationStatus> statuses);

  List<Participation> findByBundleIdIn(List<Long> bundleIds);

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

  /**
   * 입금 만료 폴링 대상. 🔴 <b>{@code flowType} 을 등가조건으로</b> 받는다 — 부등호({@code &lt;&gt; 'C2C'})로 쓰면
   * {@code (status, flow_type, due_at)} 인덱스가 {@code flow_type} 에서 동등 조건을 잃어 {@code due_at} 이
   * 범위+정렬로 이어지지 못한다. 값이 둘뿐이라 결과는 같지만 실행 계획이 달라진다.
   */
  List<Participation> findByStatusAndFlowTypeAndDueAtLessThanEqualOrderByDueAtAsc(
      ParticipationStatus status, FlowType flowType, Instant dueAt, Limit limit);


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

  /**
   * 묶음의 슬롯 전건을 <b>잠금 조회</b>한다. 「제외」가 확정 슬롯 유무를 판정하기 전에 호출해, 판정과 UPDATE
   * 사이에 개최자의 입금확인이 끼어들지 못하게 한다.
   *
   * <p><b>왜 UPDATE 의 서브쿼리로 못 하는가</b>: MySQL 은 {@code UPDATE} 대상 테이블을 서브쿼리의 FROM 에서
   * 참조하는 것을 금지한다(<b>error 1093</b> — "You can't specify target table for update in FROM clause").
   * 🔴 <b>H2 는 이것을 허용해서 단위·통합 테스트가 전부 통과했고, staging 에서야 500 으로 드러났다.</b>
   * 같은 테이블을 봐야 하는 조건은 CAS 안에 넣을 수 없고, 이렇게 잠금 조회로 앞세워야 한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Participation p WHERE p.bundleId = :bundleId")
  List<Participation> findAllByBundleIdForUpdate(@Param("bundleId") Long bundleId);

  /**
   * 개최자 「제외」 CAS — 한 묶음의 활성 슬롯 전부를 {@code HOST_RELEASED} 로 취소한다.
   *
   * <p>🔴 <b>기한 가드는 UPDATE 의 WHERE 안에 있다.</b> 밖에서 판정하고 넘기면 개최자가 반려로 기한을 민 사이
   * 옛 기한으로 통과시키는 창이 남는다. {@code b.dueAt IS NOT NULL AND b.dueAt <= :now} 라 기한이
   * 없으면(모집 중) <b>거부</b>이므로 fail-closed 다.
   *
   * <p>⚠️ <b>확정 슬롯 검사는 여기 없다.</b> 같은 테이블을 서브쿼리로 참조하면 MySQL 이 거부하기 때문이다
   * (error 1093). 대신 호출부가 {@link #findAllByBundleIdForUpdate} 로 슬롯을 잠근 뒤 판정한다 — 그 락이
   * 입금확인({@code confirmPaymentIfAwaiting})을 막아 같은 원자성을 준다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.bundleId = :bundleId AND p.status IN :releasableStatuses "
          + "AND EXISTS (SELECT b FROM ParticipationBundle b "
          + "  WHERE b.id = :bundleId AND b.closedAt IS NULL "
          + "    AND b.dueAt IS NOT NULL AND b.dueAt <= :now)")
  int releaseBundleIfDue(
      @Param("bundleId") Long bundleId,
      @Param("releasableStatuses") Collection<ParticipationStatus> releasableStatuses,
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
   * 묶음 단위 입금확인 CAS — 한 묶음의 확인 가능 슬롯 전부를 한 번에 CONFIRMED 로 전이한다.
   *
   * <p>C2C 는 「보냈어요」(PAYMENT_SENT)도 확인 대상이고, 개최자 확인이 늦어도 유효하도록 <b>기한 경과를
   * 검사하지 않는다</b> (docs/46 §3-6).
   *
   * <p>⚠️ <b>같은 테이블을 보는 조건은 넣지 않는다</b> — MySQL 이 {@code UPDATE} 대상 테이블을 서브쿼리
   * FROM 에서 참조하는 것을 금지한다(error 1093). all-or-nothing 판정은 호출부가 잠금 조회로 앞세운다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :confirmedStatus, p.confirmedAt = :now, p.updatedAt = :now "
          + "WHERE p.bundleId = :bundleId AND p.status IN :payableStatuses")
  int confirmBundleIfPayable(
      @Param("bundleId") Long bundleId,
      @Param("payableStatuses") Collection<ParticipationStatus> payableStatuses,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("now") Instant now);

  /**
   * 묶음 단위 「보냈어요」 CAS — 한 묶음의 입금 대기 슬롯 전부를 한 번에 마킹한다.
   *
   * <p>🔴 <b>기한 검사가 없다.</b> 기한이 지난 뒤에도 마킹은 가능해야 한다 — 기한 직전 입금을 보호하는 것이
   * 이 기능의 목적이고, 늦게 보낸 사람도 자기가 보냈다는 사실을 남길 수 있어야 개최자가 확인한다.
   *
   * <p>묶음이 닫혔는지는 조건에 넣지 않는다. 닫힌 묶음에는 활성 슬롯이 없으므로 0행이 되어 자연히 막힌다 —
   * 조건을 늘리는 대신 이미 성립하는 불변식에 기댄다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :sentStatus, p.paymentSentAt = :now, "
          + "    p.paymentRejectedAt = NULL, p.updatedAt = :now "
          + "WHERE p.bundleId = :bundleId AND p.status = :awaitingStatus")
  int markBundlePaymentSent(
      @Param("bundleId") Long bundleId,
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

  /**
   * 참여 → 묶음 연결 CAS. 조건부 원시 INSERT 를 건드리지 않으려고 INSERT 직후 별도로 채운다 (docs/80 ④).
   *
   * <p>{@code bundleId IS NULL} 조건은 재실행·경쟁에서 이미 붙은 묶음을 덮어쓰지 않게 한다 — 덮어쓰면 그 사람의 이체가
   * 엉뚱한 묶음으로 옮겨간다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p SET p.bundleId = :bundleId, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.bundleId IS NULL "
          // 🔴 대상 묶음의 생존을 같은 UPDATE 안에서(current read) 건다. 재사용 후보는 비잠금 조회로
          // 뽑히는데(findFirstActiveInBuncheol), 참여 생성은 분철 행 X 락을 잡고 자발 취소는 안 잡아
          // 둘이 직렬화되지 않는다. 그 사이 마지막 슬롯이 취소돼 묶음이 닫히면, 스냅샷을 믿고 그대로
          // 붙일 경우 「닫혔는데 활성 슬롯을 가진 묶음」이 생긴다 — closeIfNoActiveSlots 가
          // closed_at IS NULL 을 요구하므로 그 묶음은 두 번 다시 닫히지 않는다.
          // 새로 연 묶음은 방금 INSERT 한 행이라 이 조건이 항상 참이다 — 분기 없이 두 경로를 함께 덮는다.
          + "AND EXISTS (SELECT b FROM ParticipationBundle b "
          + "  WHERE b.id = :bundleId AND b.closedAt IS NULL)")
  int linkBundleIfUnlinked(
      @Param("id") Long id, @Param("bundleId") Long bundleId, @Param("now") Instant now);

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
