package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolRepositoryAdapter implements BuncheolRepository {

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
    // 개최자 본인이 취소한(HOST_CANCELLED) 분철만 숨기고, 인원 미달 자동취소(CANCELLED)는 개최 이력으로 노출한다.
    return jpaBuncheolRepository.findAllByHostIdAndStatusNotOrderByCreatedAtDesc(
        hostId, BuncheolStatus.HOST_CANCELLED);
  }

  /**
   * 공개 목록을 모집중(createdAt DESC) → 진행확정(deadline DESC) → 인원미달취소(deadline DESC) 세 그룹을 순서대로 이어 붙여 조회한다.
   * HOST_CANCELLED(개최자 취소)는 어느 그룹에도 포함하지 않아 목록에서 빠진다.
   *
   * <p>모집중은 createdAt, 마감·취소는 deadline 으로 정렬 컬럼이 달라 단일 {@code ORDER BY} 로는 인덱스가 정렬을 커버하지 못한다. 그룹별 전용
   * 인덱스({@code idx_buncheols_status_created} / {@code idx_buncheols_status_deadline})를 각각 타도록 쿼리를 분리하고,
   * 커서 그룹부터 시작해 limit 이 찰 때까지 다음 그룹 첫 구간을 이어 채운다 — 경계를 걸친 페이지만 쿼리가 늘고, 그 외엔 1회.
   */
  @Override
  public List<Buncheol> search(
      BuncheolSearchCondition condition, BuncheolListCursor cursor, int limit) {
    int startRank =
        cursor.isFirstPage() ? BuncheolListCursor.RANK_RECRUITING : cursor.groupRank();
    List<Buncheol> result = new ArrayList<>(limit);

    // rank0: 모집중(RECRUITING) — createdAt keyset. 첫 페이지이거나 커서가 모집중 그룹일 때만 시작점이 된다.
    if (startRank == BuncheolListCursor.RANK_RECRUITING) {
      Instant cursorCreatedAt = cursor.isFirstPage() ? null : cursor.sortAt();
      Long cursorId = cursor.isFirstPage() ? null : cursor.id();
      result.addAll(searchRecruiting(condition, cursorCreatedAt, cursorId, limit));
      if (result.size() >= limit) {
        return result;
      }
    }

    // rank1: 진행확정(CONFIRMED) — deadline keyset. 커서가 이 그룹이면 커서부터, 모집중에서 넘어왔으면 첫 구간부터.
    if (startRank <= BuncheolListCursor.RANK_CONFIRMED) {
      result.addAll(searchByDeadlineFromGroup(condition, BuncheolStatus.CONFIRMED, cursor,
          startRank == BuncheolListCursor.RANK_CONFIRMED, limit - result.size()));
      if (result.size() >= limit) {
        return result;
      }
    }

    // rank2: 인원미달 취소(CANCELLED) — deadline keyset. 맨 뒤 그룹.
    if (startRank <= BuncheolListCursor.RANK_CANCELLED) {
      result.addAll(searchByDeadlineFromGroup(condition, BuncheolStatus.CANCELLED, cursor,
          startRank == BuncheolListCursor.RANK_CANCELLED, limit - result.size()));
    }
    return result;
  }

  private List<Buncheol> searchRecruiting(
      BuncheolSearchCondition condition, Instant cursorCreatedAt, Long cursorId, int limit) {
    return jpaBuncheolRepository.searchRecruiting(
        BuncheolStatus.RECRUITING,
        condition.groupId(),
        condition.memberId(),
        condition.keyword(),
        cursorCreatedAt,
        cursorId,
        PageRequest.of(0, limit));
  }

  // deadline 정렬 그룹(CONFIRMED/CANCELLED) 조회. fromCursor 면 커서 (deadline,id) 부터, 아니면 그룹 첫 구간부터.
  private List<Buncheol> searchByDeadlineFromGroup(
      BuncheolSearchCondition condition,
      BuncheolStatus status,
      BuncheolListCursor cursor,
      boolean fromCursor,
      int limit) {
    return jpaBuncheolRepository.searchByDeadline(
        status,
        condition.groupId(),
        condition.memberId(),
        condition.keyword(),
        fromCursor ? cursor.sortAt() : null,
        fromCursor ? cursor.id() : null,
        PageRequest.of(0, limit));
  }

  @Override
  public boolean existsUnfinishedByHostId(Long hostId) {
    // C2C 입금 수집중(PAYMENT_COLLECTING) 분철도 개최자 탈퇴를 막는다 (docs/46 §4.7-D2).
    return jpaBuncheolRepository.existsUnfinishedByHostId(
        hostId,
        Set.of(BuncheolStatus.RECRUITING, BuncheolStatus.PAYMENT_COLLECTING),
        BuncheolStatus.CONFIRMED,
        Set.of(
            ParticipationStatus.APPLIED,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.PAYMENT_SENT),
        ParticipationStatus.CONFIRMED,
        DeliveryStatus.finished());
  }

  @Override
  public List<Long> findGroupIdsByBuncheolCountSince(final Instant since, final int limit) {
    return jpaBuncheolRepository.findGroupIdsByBuncheolCountSince(
        since, BuncheolStatus.active(), PageRequest.of(0, limit));
  }

  @Override
  public List<Long> findRecruitingIdsPastDeadline(final Instant now, final int limit) {
    return jpaBuncheolRepository.findIdsByStatusAndDeadlineBefore(
        BuncheolStatus.RECRUITING, now, PageRequest.of(0, limit));
  }

  @Override
  public int finalizeIfStatus(
      final Long buncheolId,
      final BuncheolStatus expectedStatus,
      final BuncheolStatus newStatus,
      final Instant now) {
    return jpaBuncheolRepository.finalizeIfStatus(buncheolId, expectedStatus, newStatus, now);
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

  @Override
  public int confirmIfAllSlotsConfirmed(
      final Long buncheolId, final long totalSlots, final Instant now) {
    return jpaBuncheolRepository.confirmIfAllSlotsConfirmed(
        buncheolId,
        totalSlots,
        BuncheolStatus.RECRUITING,
        ParticipationStatus.CONFIRMED,
        BuncheolStatus.CONFIRMED,
        now);
  }

  @Override
  public int startCollectingIfRecruiting(
      final Long buncheolId,
      final Instant paymentDueAt,
      final String bank,
      final String account,
      final String holder,
      final Instant now) {
    return jpaBuncheolRepository.startCollectingIfRecruiting(
        buncheolId,
        BuncheolStatus.RECRUITING,
        BuncheolStatus.PAYMENT_COLLECTING,
        FlowType.C2C,
        paymentDueAt,
        bank,
        account,
        holder,
        now);
  }

  @Override
  public int confirmIfAllCollected(final Long buncheolId, final Instant now) {
    return jpaBuncheolRepository.confirmIfAllCollected(
        buncheolId,
        BuncheolStatus.PAYMENT_COLLECTING,
        Set.of(
            ParticipationStatus.APPLIED,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.PAYMENT_SENT),
        ParticipationStatus.CONFIRMED,
        BuncheolStatus.CONFIRMED,
        now);
  }
}
