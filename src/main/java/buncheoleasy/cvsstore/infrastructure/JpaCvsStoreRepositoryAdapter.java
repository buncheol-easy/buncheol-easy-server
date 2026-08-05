package buncheoleasy.cvsstore.infrastructure;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.cvsstore.domain.CvsStoreCursor;
import buncheoleasy.cvsstore.domain.CvsStoreRepository;
import buncheoleasy.cvsstore.domain.RankedCvsStore;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * {@link CvsStoreCursor} 의 그룹 순서대로 지점명 일치 그룹을 먼저 채우고, limit 이 남으면 주소만 일치 그룹으로 잇는다
 * ({@code buncheol} 목록 어댑터의 그룹 이어붙이기 방식과 동일). 각 행에 소속 그룹을 태깅해 반환한다.
 */
@Repository
@RequiredArgsConstructor
public class JpaCvsStoreRepositoryAdapter implements CvsStoreRepository {

  private final JpaCvsStoreRepository jpaCvsStoreRepository;

  @Override
  public List<RankedCvsStore> searchPickupStores(
      final CvsBrand brand,
      final String escapedKeyword,
      final CvsStoreCursor cursor,
      final int limit) {
    // 키워드가 없으면 주소 그룹이 존재하지 않는다 — 전체를 지점명 그룹 하나로 조회.
    if (escapedKeyword == null) {
      return jpaCvsStoreRepository
          .searchByName(
              brand,
              null,
              cursorIdInGroup(cursor, CvsStoreCursor.RANK_NAME),
              PageRequest.of(0, limit))
          .stream()
          .map(store -> new RankedCvsStore(CvsStoreCursor.RANK_NAME, store))
          .toList();
    }

    final List<RankedCvsStore> result = new ArrayList<>();

    if (cursor.isFirstPage() || cursor.isNameGroup()) {
      jpaCvsStoreRepository
          .searchByName(
              brand,
              escapedKeyword,
              cursorIdInGroup(cursor, CvsStoreCursor.RANK_NAME),
              PageRequest.of(0, limit))
          .forEach(store -> result.add(new RankedCvsStore(CvsStoreCursor.RANK_NAME, store)));
    }

    final int remaining = limit - result.size();
    if (remaining > 0) {
      jpaCvsStoreRepository
          .searchByAddressOnly(
              brand,
              escapedKeyword,
              cursorIdInGroup(cursor, CvsStoreCursor.RANK_ADDRESS),
              PageRequest.of(0, remaining))
          .forEach(store -> result.add(new RankedCvsStore(CvsStoreCursor.RANK_ADDRESS, store)));
    }
    return result;
  }

  /** 커서가 해당 그룹 안을 가리킬 때만 keyset id 를 적용한다 (다른 그룹이면 그룹 처음부터). */
  private static Long cursorIdInGroup(final CvsStoreCursor cursor, final int groupRank) {
    if (cursor.isFirstPage() || cursor.groupRank() != groupRank) {
      return null;
    }
    return cursor.id();
  }

  @Override
  public List<CvsStore> findAll() {
    return jpaCvsStoreRepository.findAll();
  }

  @Override
  public void saveAll(final List<CvsStore> stores) {
    jpaCvsStoreRepository.saveAll(stores);
  }

  @Override
  public void deleteAllByIds(final List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jpaCvsStoreRepository.deleteAllByIdInBatch(ids);
  }
}
