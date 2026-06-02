package buncheoleasy.buncheol.domain;

import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.global.page.Cursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BuncheolRepository {

  Buncheol save(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  List<Buncheol> findAllByIds(List<Long> ids);

  /**
   * 호스트의 분철 중 사용자에게 노출 가능한 항목을 {@code createdAt DESC} 정렬로 조회한다. 취소된({@link
   * BuncheolStatus#CANCELLED}) 분철은 결과에서 제외한다.
   */
  List<Buncheol> findVisibleByHostIdOrderByCreatedAtDesc(Long hostId);

  /**
   * 활성 분철(CANCELLED 제외) 중 검색 조건에 부합하는 항목을 {@code createdAt DESC, id DESC} 정렬로 최대 {@code limit} 개
   * 조회한다.
   *
   * <p>hasNext 판별을 위해 호출 측은 보통 {@code size + 1} 을 {@code limit} 으로 넘긴다.
   */
  List<Buncheol> search(BuncheolSearchCondition condition, Cursor cursor, int limit);

  boolean existsActiveByHostId(Long hostId);

  /**
   * {@code since} 이후 등록된 분철 중 CANCELLED 가 아닌 것을 그룹별로 집계해, 등록 수가 많은 순으로 상위 {@code limit} 개 groupId 를
   * 반환한다. 동률은 groupId DESC 로 끊는다. 한 건도 없는 그룹은 결과에 포함되지 않는다.
   */
  List<Long> findGroupIdsByBuncheolCountSince(Instant since, int limit);

  /** {@code now} 기준 deadline 이 지난 RECRUITING 분철 id 를 deadline 오름차순으로 최대 {@code limit} 개 조회. 자동 마감 폴링용. */
  List<Long> findRecruitingIdsPastDeadline(Instant now, int limit);

  /**
   * 분철이 RECRUITING 일 때만 CLOSED 로 전이하는 CAS UPDATE ({@code closed_at} 기록). 자동 마감 스케줄러가 다중 인스턴스
   * 환경에서 중복 마감하지 않도록, 선점에 성공한 단일 인스턴스만 1 을 회수한다.
   *
   * <p>{@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션({@code @Transactional}) 이 필수다.
   *
   * @return 갱신된 행 수 (0 이면 이미 다른 인스턴스가 마감했거나 RECRUITING 이 아님)
   */
  int closeIfRecruiting(Long buncheolId, Instant now);
}
