package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationBundleRepository extends JpaRepository<ParticipationBundle, Long> {

  List<ParticipationBundle> findAllByBuncheolIdAndParticipantIdAndClosedAtIsNull(
      Long buncheolId, Long participantId);

  List<ParticipationBundle> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  /**
   * 묶음 닫기 CAS. 🔴 활성 슬롯 존재 판정을 <b>UPDATE 의 WHERE 서브쿼리</b>로 묶는 것이 핵심이다 — 밖에서 세어 보고
   * 넘기면 동시 취소 시 두 트랜잭션이 서로의 취소를 못 보고 둘 다 안 닫는다(포트 javadoc 참조).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationBundle b SET b.closedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :bundleId AND b.closedAt IS NULL "
          // 슬롯이 하나도 없는 묶음은 "끝난 묶음" 이 아니라 "아직 연결 전인 묶음" 이다 — 이 조건이 없으면
          // 만들자마자 닫혀 첫 참여가 곧장 시체가 된다. 백필 STEP 6 도 같은 EXISTS 를 갖고 있다.
          + "AND EXISTS (SELECT p FROM Participation p WHERE p.bundleId = b.id) "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.bundleId = b.id AND p.status IN :activeStatuses)")
  int closeIfNoActiveSlots(
      @Param("bundleId") Long bundleId,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses,
      @Param("now") Instant now);

  /** 위와 같은 조건을 분철 범위로 넓힌 일괄 판정 (취소 cascade·자동 마감). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationBundle b SET b.closedAt = :now, b.updatedAt = :now "
          + "WHERE b.buncheolId = :buncheolId AND b.closedAt IS NULL "
          + "AND EXISTS (SELECT p FROM Participation p WHERE p.bundleId = b.id) "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.bundleId = b.id AND p.status IN :activeStatuses)")
  int closeEmptyByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses,
      @Param("now") Instant now);

  /**
   * 묶음 기한을 뒤로 민다 (개최자 반려). {@code b.dueAt < :dueAt} 조건이 핵심 — 없으면 반려가 기한을 <b>앞으로
   * 당겨</b> 「제외」를 열어 버릴 수 있다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationBundle b SET b.dueAt = :dueAt, b.updatedAt = :now "
          + "WHERE b.id = :bundleId AND b.closedAt IS NULL "
          + "AND (b.dueAt IS NULL OR b.dueAt < :dueAt)")
  int extendDueAt(
      @Param("bundleId") Long bundleId,
      @Param("dueAt") Instant dueAt,
      @Param("now") Instant now);

  /**
   * 「보냈어요」 시각을 묶음에 기록한다 — <b>이 값이 정본</b>이다. 이체가 한 번이므로 신고도 한 번이다.
   *
   * <p>자리에도 같은 시각이 찍히지만(상태 전이 CAS 와 한 몸이다) 그건 사본이고, 읽기가 전부 이쪽으로
   * 옮겨진 뒤 제거한다 — 배송비·배송지와 같은 순서다.
   *
   * <p>재마킹(더블탭·부분 마킹)에서도 덮어쓴다. 자리 칸이 이미 "가장 최근 신고 시각" 이라 의미를 맞춘다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationBundle b SET b.paymentSentAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :bundleId AND b.closedAt IS NULL")
  int markPaymentSent(@Param("bundleId") Long bundleId, @Param("now") Instant now);

  /** 성사 확정 시 기한 없이 열려 있던 활성 묶음에 입금 기한을 채운다. 이미 채워진 묶음은 건드리지 않는다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationBundle b SET b.dueAt = :dueAt, b.updatedAt = :now "
          + "WHERE b.buncheolId = :buncheolId AND b.closedAt IS NULL AND b.dueAt IS NULL")
  int assignDueAtByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("dueAt") Instant dueAt,
      @Param("now") Instant now);
}
