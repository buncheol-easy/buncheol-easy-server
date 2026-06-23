package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolRepositoryAdapter implements BuncheolRepository {

  // 호스트의 '진행 중인 분철' 집합 (회원탈퇴 가드용). 모집 중이거나 진행확정된 분철이 있으면 탈퇴할 수 없다.
  private static final Set<BuncheolStatus> ACTIVE_STATUSES =
      EnumSet.of(BuncheolStatus.RECRUITING, BuncheolStatus.CONFIRMED);

  private final JpaBuncheolRepository jpaBuncheolRepository;

  @Override
  public Buncheol save(Buncheol buncheol) {
    return jpaBuncheolRepository.save(buncheol);
  }

  @Override
  public Optional<Buncheol> findById(Long id) {
    return jpaBuncheolRepository.findById(id);
  }

  @Override
  public List<Buncheol> findAllByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolRepository.findAllById(ids);
  }

  @Override
  public List<Buncheol> findVisibleByHostIdOrderByCreatedAtDesc(Long hostId) {
    return jpaBuncheolRepository.findAllByHostIdAndStatusNotOrderByCreatedAtDesc(
        hostId, BuncheolStatus.CANCELLED);
  }

  /**
   * 공개 목록을 모집중(createdAt DESC) → 마감(deadline DESC) 두 그룹을 이어 붙여 조회한다.
   *
   * <p>두 그룹은 정렬 컬럼이 달라 단일 {@code ORDER BY} 로는 어떤 인덱스도 정렬을 커버하지 못하고 매 페이지 filesort 가 발생한다. 그룹별 전용 인덱스(모집중
   * {@code idx_buncheols_status_created}, 마감 {@code idx_buncheols_status_deadline}) 를 각각 타도록 쿼리를 분리하고,
   * 한 페이지가 그룹 경계를 걸칠 때만(모집중이 limit 을 못 채울 때) 마감 그룹 첫 구간을 이어 채운다 — 경계 페이지만 쿼리 2회, 그 외엔 1회.
   */
  @Override
  public List<Buncheol> search(
      BuncheolSearchCondition condition, BuncheolListCursor cursor, int limit) {
    // 커서가 이미 마감 그룹에 진입한 경우: 마감 그룹만 deadline keyset 으로 이어 조회한다.
    if (!cursor.isFirstPage() && !cursor.isRecruitingGroup()) {
      return searchConfirmed(condition, cursor.sortAt(), cursor.id(), limit);
    }

    // 첫 페이지이거나 모집중 그룹을 진행 중인 경우: 모집중을 createdAt keyset 으로 먼저 채운다.
    Instant recruitingCursorCreatedAt = cursor.isFirstPage() ? null : cursor.sortAt();
    Long recruitingCursorId = cursor.isFirstPage() ? null : cursor.id();
    List<Buncheol> recruiting =
        jpaBuncheolRepository.searchRecruiting(
            BuncheolStatus.RECRUITING,
            condition.groupId(),
            condition.memberId(),
            condition.keyword(),
            recruitingCursorCreatedAt,
            recruitingCursorId,
            PageRequest.of(0, limit));
    if (recruiting.size() >= limit) {
      return recruiting;
    }

    // 모집중이 limit 을 못 채움 = 모집중 소진 → 남은 자리를 마감 그룹 첫 구간으로 잇는다.
    List<Buncheol> confirmed =
        searchConfirmed(condition, null, null, limit - recruiting.size());
    List<Buncheol> combined = new ArrayList<>(recruiting.size() + confirmed.size());
    combined.addAll(recruiting);
    combined.addAll(confirmed);
    return combined;
  }

  private List<Buncheol> searchConfirmed(
      BuncheolSearchCondition condition, Instant cursorDeadline, Long cursorId, int limit) {
    return jpaBuncheolRepository.searchConfirmed(
        BuncheolStatus.CONFIRMED,
        condition.groupId(),
        condition.memberId(),
        condition.keyword(),
        cursorDeadline,
        cursorId,
        PageRequest.of(0, limit));
  }

  @Override
  public boolean existsActiveByHostId(Long hostId) {
    return jpaBuncheolRepository.existsByHostIdAndStatusIn(hostId, ACTIVE_STATUSES);
  }

  @Override
  public List<Long> findGroupIdsByBuncheolCountSince(final Instant since, final int limit) {
    return jpaBuncheolRepository.findGroupIdsByBuncheolCountSince(
        since, BuncheolStatus.notCancelled(), PageRequest.of(0, limit));
  }

  @Override
  public List<Long> findRecruitingIdsPastDeadline(final Instant now, final int limit) {
    return jpaBuncheolRepository.findIdsByStatusAndDeadlineBefore(
        BuncheolStatus.RECRUITING, now, PageRequest.of(0, limit));
  }

  @Override
  public int finalizeIfRecruiting(
      final Long buncheolId, final BuncheolStatus newStatus, final Instant now) {
    return jpaBuncheolRepository.finalizeIfRecruiting(
        buncheolId, newStatus, BuncheolStatus.RECRUITING, now);
  }

  @Override
  public int finalizeExpiredByConfirmedHeadcount(final Long buncheolId, final Instant now) {
    return jpaBuncheolRepository.finalizeExpiredByConfirmedHeadcount(
        buncheolId,
        BuncheolStatus.RECRUITING,
        ParticipationStatus.CONFIRMED,
        BuncheolStatus.CONFIRMED,
        BuncheolStatus.CANCELLED,
        now);
  }
}
