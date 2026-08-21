package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.DeliveryStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolRepository extends JpaRepository<Buncheol, Long> {

  long countByHostIdAndStatusIn(Long hostId, Set<BuncheolStatus> statuses);

  long countByGroupIdAndStatus(Long groupId, BuncheolStatus status);

  List<Buncheol> findAllByHostIdAndStatusNotOrderByCreatedAtDesc(
      Long hostId, BuncheolStatus excludedStatus);

  /**
   * 공개 목록의 <b>모집중 그룹</b>을 {@code createdAt DESC, id DESC}(최신 개최순) 로 검색한다. 어댑터에서 {@link
   * BuncheolStatus#RECRUITING} 과 {@link BuncheolStatus#PAYMENT_COLLECTING} 을 함께 전달한다 — 둘 다 아직 진행 중인
   * 분철이라 같은 그룹·같은 정렬 축(createdAt)을 쓴다 ({@link
   * buncheoleasy.buncheol.domain.BuncheolListCursor#RANK_RECRUITING}).
   *
   * <p>검색어는 정규화된 {@code searchTitle}(공백·구두점 무시) 과 애플리케이션이 해석해 넘긴 그룹·멤버 id, 그리고 설명 원문
   * 부분일치에 걸린다. 설명은 정규화 컬럼이 없어 원문 비교라 공백을 무시하지 않는다.
   *
   * <p>{@code normalizedKeyword} 의 {@code IS NOT NULL} 가드는 생략할 수 없다 — MySQL 은 {@code CONCAT('%',
   * NULL, '%')} 를 NULL 로 접어 매칭 0건이 되지만 H2 는 {@code '%%'} 로 접어 전 행이 매칭된다. 가드가 없으면
   * 구두점만 입력한 검색이 테스트(H2)에서는 전건, 운영(MySQL)에서는 0건으로 갈린다.
   *
   * <p>각 필터는 인자가 {@code null} 이면 미적용된다. 커서가 있으면 {@code (createdAt, id)} 미만으로 keyset 페이지네이션한다.
   *
   * <p><b>⚠️ 이 쿼리는 정렬을 인덱스로 커버하지 못한다.</b> {@code idx_buncheols_status_created (status, created_at
   * DESC, id DESC)} 는 선행 컬럼이 상수일 때만 정렬을 커버하는데, 상태가 둘이라 {@code IN} = range 스캔이 되어 두 구간을 읽은 뒤
   * 정렬해야 한다. staging MySQL 실측(2026-08-21):
   *
   * <pre>
   * status = 'RECRUITING'                          → type=ref   Extra: Using index
   * status IN ('RECRUITING','PAYMENT_COLLECTING')  → type=range Extra: Using where; Using index; Using filesort
   * </pre>
   *
   * <p>커버링 인덱스라 테이블 접근은 없지만 {@code LIMIT} 조기 종료가 사라져, 조건에 걸리는 두 상태의 <b>전 행</b>을 읽고 filesort 한다.
   * 활성 분철이 수십 건인 현 규모에서는 수용 가능하다고 보고 받아들였다 — 이 판단은 <b>활성 분철 수</b>에 달려 있으므로, 목록 지연이
   * 관측되면 여기부터 본다. 고칠 때는 그룹 순위를 접은 생성 컬럼({@code list_rank}) + {@code (list_rank, created_at DESC, id
   * DESC)} 인덱스가, rank0 을 두 쿼리로 쪼개 애플리케이션에서 머지하는 것보다 깔끔하다.
   *
   * <p>{@code groupId} 필터가 붙는 경로는 {@code idx_buncheols_group_created} 를 타므로 영향이 없다.
   */
  @Query(
      "SELECT b FROM Buncheol b "
          + "WHERE b.status IN :statuses "
          + "  AND (:groupId IS NULL OR b.groupId = :groupId) "
          + "  AND (:onlyFavoriteGroups = FALSE OR b.groupId IN :favoriteGroupIds) "
          + "  AND (:memberId IS NULL OR b.id IN "
          + "        (SELECT bm.buncheolId FROM BuncheolMember bm WHERE bm.memberId = :memberId)) "
          + "  AND (:keyword IS NULL "
          + "        OR (:normalizedKeyword IS NOT NULL "
          + "            AND b.searchTitle LIKE CONCAT('%', :normalizedKeyword, '%') ESCAPE '\\') "
          + "        OR b.groupId IN :keywordGroupIds "
          + "        OR EXISTS (SELECT bm2 FROM BuncheolMember bm2 "
          + "                   WHERE bm2.buncheolId = b.id "
          + "                   AND bm2.memberId IN :keywordMemberIds) "
          + "        OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
          + "  AND (:cursorCreatedAt IS NULL "
          + "        OR b.createdAt < :cursorCreatedAt "
          + "        OR (b.createdAt = :cursorCreatedAt AND b.id < :cursorId)) "
          + "ORDER BY b.createdAt DESC, b.id DESC")
  List<Buncheol> searchRecruiting(
      @Param("statuses") Set<BuncheolStatus> statuses,
      @Param("groupId") Long groupId,
      @Param("onlyFavoriteGroups") boolean onlyFavoriteGroups,
      @Param("favoriteGroupIds") List<Long> favoriteGroupIds,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("normalizedKeyword") String normalizedKeyword,
      @Param("keywordGroupIds") List<Long> keywordGroupIds,
      @Param("keywordMemberIds") List<Long> keywordMemberIds,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /**
   * 공개 목록의 <b>deadline 기준 그룹</b>(진행확정 {@link BuncheolStatus#CONFIRMED} 또는 인원미달취소 {@link
   * BuncheolStatus#CANCELLED})을 {@code deadline DESC, id DESC}(현재와 가까운 마감순) 로 검색한다. 어댑터에서 그룹별 status 를
   * 전달해 두 그룹이 같은 쿼리를 공유한다.
   *
   * <p>각 필터는 인자가 {@code null} 이면 미적용된다. 커서가 있으면 {@code (deadline, id)} 미만으로 keyset 페이지네이션한다. {@code
   * idx_buncheols_status_deadline (status, deadline, id)} 인덱스를 역방향 스캔해 {@code deadline DESC, id DESC} 정렬을
   * 커버한다.
   */
  @Query(
      "SELECT b FROM Buncheol b "
          + "WHERE b.status = :status "
          + "  AND (:groupId IS NULL OR b.groupId = :groupId) "
          + "  AND (:onlyFavoriteGroups = FALSE OR b.groupId IN :favoriteGroupIds) "
          + "  AND (:memberId IS NULL OR b.id IN "
          + "        (SELECT bm.buncheolId FROM BuncheolMember bm WHERE bm.memberId = :memberId)) "
          + "  AND (:keyword IS NULL "
          + "        OR (:normalizedKeyword IS NOT NULL "
          + "            AND b.searchTitle LIKE CONCAT('%', :normalizedKeyword, '%') ESCAPE '\\') "
          + "        OR b.groupId IN :keywordGroupIds "
          + "        OR EXISTS (SELECT bm2 FROM BuncheolMember bm2 "
          + "                   WHERE bm2.buncheolId = b.id "
          + "                   AND bm2.memberId IN :keywordMemberIds) "
          + "        OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
          + "  AND (:cursorDeadline IS NULL "
          + "        OR b.deadline < :cursorDeadline "
          + "        OR (b.deadline = :cursorDeadline AND b.id < :cursorId)) "
          + "ORDER BY b.deadline DESC, b.id DESC")
  List<Buncheol> searchByDeadline(
      @Param("status") BuncheolStatus status,
      @Param("groupId") Long groupId,
      @Param("onlyFavoriteGroups") boolean onlyFavoriteGroups,
      @Param("favoriteGroupIds") List<Long> favoriteGroupIds,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("normalizedKeyword") String normalizedKeyword,
      @Param("keywordGroupIds") List<Long> keywordGroupIds,
      @Param("keywordMemberIds") List<Long> keywordMemberIds,
      @Param("cursorDeadline") Instant cursorDeadline,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /**
   * 호스트에게 아직 끝나지 않은 분철이 있는지 (회원탈퇴 가드). 모집중이거나, 진행확정됐지만 호스트가 입금확인해 줘야 할 참여(입금 확인 중)가
   * 남아 있거나, 입금확인 참여 중 배송이 끝나지 않은 건이 하나라도 남아 있으면 끝나지 않은 분철로 판정한다 (배송 스냅샷이 없는 입금확인 참여도
   * 미종료로 본다). 진행확정 분철의 입금 확인 중 참여는 만료 스케줄러가 곧 취소하는 게 정상 흐름이지만, 스케줄러 지연 사이에 호스트가 탈퇴하면
   * 해당 참여자의 참여 상세(호스트 조회)가 깨지므로 배송 조건 없이 미종료로 본다.
   */
  @Query(
      "SELECT COUNT(b) > 0 FROM Buncheol b "
          + "WHERE b.hostId = :hostId "
          + "AND (b.status IN :openStatuses "
          + "  OR (b.status = :confirmedStatus "
          + "    AND EXISTS ("
          + "      SELECT p FROM Participation p "
          + "      WHERE p.buncheolId = b.id "
          + "      AND (p.status IN :pendingParticipationStatuses "
          + "        OR (p.status = :confirmedParticipationStatus "
          + "          AND NOT EXISTS ("
          + "            SELECT d FROM Delivery d "
          + "            WHERE d.participationId = p.id "
          + "            AND d.status IN :finishedDeliveryStatuses))))))")
  boolean existsUnfinishedByHostId(
      @Param("hostId") Long hostId,
      @Param("openStatuses") Set<BuncheolStatus> openStatuses,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("pendingParticipationStatuses") Set<ParticipationStatus> pendingParticipationStatuses,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("finishedDeliveryStatuses") Set<DeliveryStatus> finishedDeliveryStatuses);

  // 최근 N일 분철 등록 수 상위 그룹 id 만 인기도 순으로 반환. Group 본문 매핑은 호출 측 책임.
  // 부정 조건(`status <> CANCELLED`) 대신 IN 절을 써 옵티마이저가 인덱스 활용을 안정적으로 판단하게 한다.
  @Query(
      "SELECT b.groupId FROM Buncheol b "
          + "WHERE b.status IN :statuses AND b.createdAt >= :since "
          + "GROUP BY b.groupId "
          + "ORDER BY COUNT(b) DESC, b.groupId DESC")
  List<Long> findGroupIdsByBuncheolCountSince(
      @Param("since") Instant since,
      @Param("statuses") Set<BuncheolStatus> statuses,
      Pageable pageable);

  // 마감 판정 대상 분철 id (자동 마감 폴링용, deadline 오름차순). LEGACY 는 deadline 즉시, C2C 는 확정 유예(48h)까지
  // 지나야 대상이 된다 (docs/46 §7.1-5) — 유예 중 C2C 분철이 매 주기 배치 슬롯(LIMIT)을 잠식하지 않게 쿼리에서 거른다.
  @Query(
      "SELECT b.id FROM Buncheol b "
          + "WHERE b.status = :status AND ("
          + "  (b.flowType = :legacyFlow AND b.deadline <= :now) "
          + "  OR (b.flowType = :c2cFlow AND b.deadline <= :graceCutoff)) "
          + "ORDER BY b.deadline ASC")
  List<Long> findIdsByStatusAndDeadlineBefore(
      @Param("status") BuncheolStatus status,
      @Param("legacyFlow") FlowType legacyFlow,
      @Param("c2cFlow") FlowType c2cFlow,
      @Param("now") Instant now,
      @Param("graceCutoff") Instant graceCutoff,
      Pageable pageable);

  // 입금 수집중(PAYMENT_COLLECTING)이고 일괄 입금 기한이 지난 C2C 분철 id (데드엔드 정리 폴링용).
  @Query(
      "SELECT b.id FROM Buncheol b "
          + "WHERE b.status = :status AND b.paymentDueAt <= :now "
          + "ORDER BY b.paymentDueAt ASC")
  List<Long> findIdsByStatusAndPaymentDueBefore(
      @Param("status") BuncheolStatus status, @Param("now") Instant now, Pageable pageable);

  /**
   * C2C 데드엔드 정리 CAS: 입금 수집중인데 활성 참여가 하나도 남지 않았으면(전원 만료·자발취소, 확정 0건) 미성사 취소한다. 확정 참여가 있으면
   * 전이하지 않는다 — 부분 확정/취소는 개최자 선택으로 남긴다 (docs/46 §7.1-6). {@code finalizedAt} 은 성사 확정 시각을 보존한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :cancelledStatus, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :collectingStatus "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.buncheolId = b.id AND p.status IN :activeStatuses)")
  int cancelIfCollectingAndEmpty(
      @Param("buncheolId") Long buncheolId,
      @Param("collectingStatus") BuncheolStatus collectingStatus,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses,
      @Param("cancelledStatus") BuncheolStatus cancelledStatus,
      @Param("now") Instant now);

  /**
   * C2C 입금 수집중 분철의 개최자 취소 CAS: 입금확인(CONFIRMED)된 참여가 하나도 없을 때만 HOST_CANCELLED 로 전이한다 (docs/56
   * H-13). 확정 참여가 이미 있으면 그 돈은 개최자 계좌에 있고 플랫폼이 환불을 강제할 수 없으므로 취소 자체를 막는다 — 문의 경유 환불 후 처리한다.
   * 존재 판정을 UPDATE WHERE 서브쿼리(current read)로 묶어, 개최자가 입금확인과 분철취소를 동시에 실행해도 취소가 새어 나가지 않는다.
   *
   * <p>⚠️ 락 순서는 분철 행 → 참여 행이다. C2C 입금확인(참여 행 → {@link #confirmIfAllCollected} 의 분철 행)과 역순이라,
   * 위 동시 실행이 실제로 겹치면 정합성은 지켜지지만 실패 모드가 409 가 아니라 InnoDB 데드락 롤백이 될 수 있다(레포에 재시도 핸들러 없음).
   * 같은 형태가 {@link #cancelIfCollectingAndEmpty} 에 이미 있어 새로 생긴 리스크 클래스는 아니다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :cancelledStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :collectingStatus "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.buncheolId = b.id AND p.status = :confirmedStatus)")
  int hostCancelIfCollectingAndNoConfirmed(
      @Param("buncheolId") Long buncheolId,
      @Param("collectingStatus") BuncheolStatus collectingStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("cancelledStatus") BuncheolStatus cancelledStatus,
      @Param("now") Instant now);

  /** C2C 참여 생성 직렬화용 잠금 조회 — 다슬롯 첫 참여 판정(배송비 1회·스냅샷 일치)의 TOCTOU 방지 (docs/46 §4.7-A1·A2). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Buncheol b WHERE b.id = :id")
  Optional<Buncheol> findByIdForUpdate(@Param("id") Long id);

  // expectedStatus → newStatus CAS UPDATE (호스트 취소 공용: RECRUITING/CANCELLED → HOST_CANCELLED).
  // 선점한 단일 인스턴스만 1 을 회수해 다중 인스턴스 중복 마감과 마감/취소 경합을 막는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :newStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :expectedStatus")
  int finalizeIfStatus(
      @Param("buncheolId") Long buncheolId,
      @Param("expectedStatus") BuncheolStatus expectedStatus,
      @Param("newStatus") BuncheolStatus newStatus,
      @Param("now") Instant now);

  // 마감 판정 전용 CAS: 입금확인된(CONFIRMED) 참여 수를 서브쿼리로 세어 minHeadcount 충족 여부에 따라 CONFIRMED/CANCELLED 로
  // 한 UPDATE 안에서 전이한다. count 를 별도 SELECT 로 먼저 읽으면(REPEATABLE READ 스냅샷) 그 사이 입금확인이 커밋돼 stale count 로
  // 오판(충족인데 CANCELLED)할 수 있어, 카운트·비교·전이를 한 UPDATE 로 묶는다.
  // 단, 이 원자성은 InnoDB 가 UPDATE 평가 시 서브쿼리를 current read(최신 커밋) 로 보는 동작에 의존한다(SQL 표준 보장 아님).
  // H2 통합 테스트는 단일 스레드 기능(CONFIRMED/CANCELLED/이미마감 분기)만 검증하며 동시성 레이스 자체를 재현하지는 않는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = CASE WHEN ("
          + "      SELECT COUNT(p) FROM Participation p "
          + "      WHERE p.buncheolId = b.id AND p.status = :confirmedParticipationStatus"
          + "    ) >= b.minHeadcount THEN :confirmedStatus ELSE :cancelledStatus END, "
          + "  b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus")
  int finalizeExpiredByConfirmedHeadcount(
      @Param("buncheolId") Long buncheolId,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("cancelledStatus") BuncheolStatus cancelledStatus,
      @Param("now") Instant now);

  // 조기 진행확정 전용 CAS: 입금확인(CONFIRMED) 참여 수가 전체 슬롯 수 이상(매진+전원확정)이고 minHeadcount 도 충족할 때만
  // RECRUITING → CONFIRMED. count 를 UPDATE WHERE 서브쿼리로 current-read 로 세므로, 마지막 슬롯들을 동시에 입금확인하는
  // 경합에서도 non-locking COUNT 를 미리 읽고 판정하는 read-then-act 갭 없이 즉시 확정한다(이 CAS 가 확정하면 이후 deadline 스케줄러는
  // status 불일치로 이중 확정하지 않는다).
  // totalSlots=0 이면 minHeadcount(≥1) <= 0 이 거짓이라 전이하지 않는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :confirmedStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus "
          + "AND b.minHeadcount <= :totalSlots "
          + "AND ("
          + "  SELECT COUNT(p) FROM Participation p "
          + "  WHERE p.buncheolId = b.id AND p.status = :confirmedParticipationStatus"
          + ") >= :totalSlots")
  int confirmIfAllSlotsConfirmed(
      @Param("buncheolId") Long buncheolId,
      @Param("totalSlots") long totalSlots,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("now") Instant now);

  // --- C2C 플로우 CAS (docs/46 §4) ---

  /**
   * C2C 성사 확정 CAS (RECRUITING → PAYMENT_COLLECTING). 일괄 입금 기한과 확정 시점 개최자 계좌 스냅샷을 함께 기록한다 —
   * 확정 후 개최자가 프로필 계좌를 바꿔도 안내 계좌가 어긋나지 않는다 (docs/46 §4.1·§4.7-B1).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :collectingStatus, b.paymentDueAt = :paymentDueAt, b.finalizedAt = :now, "
          + "    b.paymentAccount.bank = :bank, b.paymentAccount.account = :account, "
          + "    b.paymentAccount.holder = :holder, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus AND b.flowType = :c2cFlow")
  int startCollectingIfRecruiting(
      @Param("buncheolId") Long buncheolId,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("collectingStatus") BuncheolStatus collectingStatus,
      @Param("c2cFlow") FlowType c2cFlow,
      @Param("paymentDueAt") Instant paymentDueAt,
      @Param("bank") String bank,
      @Param("account") String account,
      @Param("holder") String holder,
      @Param("now") Instant now);

  /**
   * C2C 전원 입금확인 CAS: 미확정 활성 참여(APPLIED·AWAITING_PAYMENT·PAYMENT_SENT)가 없고 확정 참여가 1건 이상이면
   * PAYMENT_COLLECTING → CONFIRMED 로 전이한다. 서브쿼리 current-read 의존은 {@code confirmIfAllSlotsConfirmed}
   * 주석 참조.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :confirmedStatus, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :collectingStatus "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.buncheolId = b.id AND p.status IN :pendingStatuses) "
          + "AND EXISTS (SELECT p2 FROM Participation p2 "
          + "  WHERE p2.buncheolId = b.id AND p2.status = :confirmedParticipationStatus)")
  int confirmIfAllCollected(
      @Param("buncheolId") Long buncheolId,
      @Param("collectingStatus") BuncheolStatus collectingStatus,
      @Param("pendingStatuses") Collection<ParticipationStatus> pendingStatuses,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("now") Instant now);
}
