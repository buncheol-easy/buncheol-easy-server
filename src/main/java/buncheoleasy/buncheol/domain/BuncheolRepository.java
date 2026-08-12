package buncheoleasy.buncheol.domain;

import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BuncheolRepository {

  Buncheol save(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  List<Buncheol> findAllByIds(List<Long> ids);

  /**
   * 호스트의 분철 중 사용자에게 노출 가능한 항목을 {@code createdAt DESC} 정렬로 조회한다. 개최자 취소({@link
   * BuncheolStatus#HOST_CANCELLED}) 분철만 제외하고, 인원 미달 자동취소({@link BuncheolStatus#CANCELLED})는 개최 이력으로 포함한다.
   */
  List<Buncheol> findVisibleByHostIdOrderByCreatedAtDesc(Long hostId);

  /**
   * 공개 목록에 노출 가능한 분철(개최자 취소 {@link BuncheolStatus#HOST_CANCELLED} 만 제외)을 공개 목록 정렬로 최대 {@code limit} 개 조회한다.
   *
   * <p>정렬은 세 그룹을 이어 붙인다: 모집중(RECRUITING) 을 {@code createdAt DESC}(최신 개최순) → 진행확정(CONFIRMED) 을 {@code
   * deadline DESC}(현재와 가까운 마감순) → 인원미달취소(CANCELLED) 를 {@code deadline DESC} 로 잇는다. 모든 그룹 동일 시각은 {@code id
   * DESC} 로 끊는다.
   *
   * <p>커서는 {@link BuncheolListCursor} 로, 마지막으로 본 그룹·정렬 시각·id 를 담는다. hasNext 판별을 위해 호출 측은 보통 {@code size +
   * 1} 을 {@code limit} 으로 넘긴다.
   */
  List<Buncheol> search(BuncheolSearchCondition condition, BuncheolListCursor cursor, int limit);

  /**
   * 호스트에게 아직 끝나지 않은 분철이 하나라도 있는지 (회원탈퇴 가드용). 모집중({@link BuncheolStatus#RECRUITING})이거나, 진행확정({@link
   * BuncheolStatus#CONFIRMED})됐지만 호스트가 입금확인해 줘야 할 참여(입금 확인 중)가 남아 있거나, 입금확인 참여 중 배송이 끝나지 않은({@link
   * buncheoleasy.delivery.domain.DeliveryStatus#finished()} 이전) 건이 남아 있으면 끝나지 않은 분철로 본다. 취소된 분철과 전
   * 참여 배송이 끝난 진행확정 분철은 탈퇴를 막지 않는다 (배송비 환급 진행 여부는 보지 않는다).
   */
  boolean existsUnfinishedByHostId(Long hostId);

  /** 그룹의 모집중({@link BuncheolStatus#RECRUITING}) 분철 수. 아티스트 페이지 헤더 표기용. */
  long countRecruitingByGroupId(Long groupId);

  /** 활성(모집중·입금 수집중) 개최 수 — 일반 유저 개최 상한 판정용. */
  long countActiveByHostId(Long hostId);

  /**
   * {@code since} 이후 등록된 분철 중 CANCELLED 가 아닌 것을 그룹별로 집계해, 등록 수가 많은 순으로 상위 {@code limit} 개 groupId 를
   * 반환한다. 동률은 groupId DESC 로 끊는다. 한 건도 없는 그룹은 결과에 포함되지 않는다.
   */
  List<Long> findGroupIdsByBuncheolCountSince(Instant since, int limit);

  /** {@code now} 기준 deadline 이 지난 RECRUITING 분철 id 를 deadline 오름차순으로 최대 {@code limit} 개 조회. 자동 마감 폴링용. */
  /**
   * 마감 판정 대상 모집중 분철 id. LEGACY 는 deadline 경과 즉시, C2C 는 {@code c2cGraceCutoff}(= now - 확정 유예 48h)
   * 이전 deadline 만 대상이다 — 유예 중 C2C 분철이 폴링 배치를 잠식하지 않게 쿼리에서 거른다 (docs/46 §7.1-5).
   */
  List<Long> findRecruitingIdsPastDeadline(Instant now, Instant c2cGraceCutoff, int limit);

  /**
   * 분철이 {@code expectedStatus} 일 때만 {@code newStatus} 로 전이하는 CAS UPDATE ({@code finalized_at}
   * 기록). 호스트 취소(RECRUITING/CANCELLED → HOST_CANCELLED)가 사용하며, 마감 스케줄러·취소 경합 상황에서 선점에 성공한 한쪽만 1 을
   * 회수한다.
   *
   * <p>{@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션({@code @Transactional}) 이 필수다.
   *
   * @return 갱신된 행 수 (0 이면 이미 다른 인스턴스가 전이했거나 {@code expectedStatus} 가 아님)
   */
  int finalizeIfStatus(
      Long buncheolId, BuncheolStatus expectedStatus, BuncheolStatus newStatus, Instant now);

  /**
   * 마감 판정 전용 CAS. 입금확인된(CONFIRMED) 참여 수가 {@code minHeadcount} 이상이면 CONFIRMED, 미만이면 CANCELLED 로 {@code
   * RECRUITING} 인 분철을 단일 UPDATE 로 원자 전이한다(카운트·비교·전이를 한 current-read 쿼리로 묶어 stale count 오판을 방지).
   *
   * <p>{@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션이 필수다. 전이된 실제 상태(CONFIRMED/CANCELLED)는 반환하지 않으므로
   * 후속 분기는 재조회로 판별한다.
   *
   * @return 갱신된 행 수 (0 이면 이미 마감됐거나 RECRUITING 이 아님)
   */
  int finalizeExpiredByConfirmedHeadcount(Long buncheolId, Instant now);

  /**
   * 전 슬롯 조기 진행확정 전용 CAS. 입금확인(CONFIRMED) 참여 수가 {@code totalSlots}(전체 멤버 슬롯 수) 이상이고 {@code
   * minHeadcount} 도 충족할 때만 {@code RECRUITING} 인 분철을 CONFIRMED 로 전이한다. count 를 UPDATE WHERE 서브쿼리로
   * 세어(current read), 마지막 슬롯들을 동시에 입금확인하는 경합에서도 조기 확정을 놓치지 않는다.
   *
   * <p>{@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션이 필수다.
   *
   * @return 갱신된 행 수 (0 이면 아직 매진 아님 / 최소인원 미달 / 이미 마감됨)
   */
  int confirmIfAllSlotsConfirmed(Long buncheolId, long totalSlots, Instant now);

  // --- C2C 플로우 CAS (docs/46 §4) ---

  /**
   * C2C 성사 확정 CAS (RECRUITING → PAYMENT_COLLECTING). 일괄 입금 기한과 확정 시점 개최자 계좌 스냅샷을 함께 기록한다.
   * C2C 분철에만 적용된다(flow_type 조건). {@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션이 필수다.
   *
   * @return 갱신된 행 수 (0 이면 RECRUITING 이 아니거나 C2C 분철이 아님)
   */
  int startCollectingIfRecruiting(
      Long buncheolId, Instant paymentDueAt, String bank, String account, String holder, Instant now);

  /**
   * C2C 전원 입금확인 CAS: 미확정 활성 참여가 없고 확정 참여가 1건 이상일 때만 PAYMENT_COLLECTING → CONFIRMED 로 전이한다.
   * {@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션이 필수다.
   *
   * @return 갱신된 행 수 (0 이면 아직 미확정 참여가 남았거나 입금 수집중이 아님)
   */
  int confirmIfAllCollected(Long buncheolId, Instant now);

  /** 입금 수집중이고 일괄 입금 기한이 지난 C2C 분철 id (데드엔드 정리 폴링용, 기한 오름차순). */
  List<Long> findCollectingIdsPastPaymentDue(Instant now, int limit);

  /**
   * C2C 데드엔드 정리 CAS: 입금 수집중인데 활성 참여가 하나도 남지 않았으면(전원 만료·자발취소, 확정 0건) 미성사 취소한다. 확정 참여가 있으면
   * 전이하지 않는다 — 부분 확정/취소는 개최자 선택으로 남긴다 (docs/46 §7.1-6).
   *
   * @return 갱신된 행 수 (0 이면 활성 참여가 남았거나 입금 수집중이 아님)
   */
  int cancelIfCollectingAndEmpty(Long buncheolId, Instant now);

  /**
   * C2C 참여 생성 직렬화용 잠금 조회 (SELECT ... FOR UPDATE). 다슬롯 첫 참여 판정(배송비 1회·배송지/입금자명 스냅샷 일치)의
   * check-then-insert 레이스를 분철 행 락으로 막는다 (docs/46 §4.7-A1·A2). 호출 측 트랜잭션이 필수다.
   */
  Optional<Buncheol> findByIdForUpdate(Long id);
}
