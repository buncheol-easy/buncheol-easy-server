package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.global.page.Cursor;
import java.time.Instant;
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

  private static final Set<BuncheolStatus> ACTIVE_STATUSES =
      EnumSet.of(
          BuncheolStatus.RECRUITING,
          BuncheolStatus.CLOSED,
          BuncheolStatus.PAID,
          BuncheolStatus.SETTLING);

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

  @Override
  public List<Buncheol> search(BuncheolSearchCondition condition, Cursor cursor, int limit) {
    Instant cursorCreatedAt = cursor.isFirstPage() ? null : cursor.createdAt();
    Long cursorId = cursor.isFirstPage() ? null : cursor.id();
    return jpaBuncheolRepository.search(
        BuncheolStatus.CANCELLED,
        condition.groupId(),
        condition.memberId(),
        condition.keyword(),
        cursorCreatedAt,
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
        since, BuncheolStatus.activeOrFinished(), PageRequest.of(0, limit));
  }
}
