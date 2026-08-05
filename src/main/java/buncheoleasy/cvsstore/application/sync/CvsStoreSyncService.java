package buncheoleasy.cvsstore.application.sync;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.cvsstore.domain.CvsStoreRepository;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크롤러가 S3 에 게시한 스냅샷을 마스터(cvs_stores)에 diff 적용하고, 이어서 접수처 점포 코드가 연결된
 * 배송지(shipping_addresses)의 지점명을 마스터 기준으로 정합화한다. 두 단계를 한 실행으로 묶어 "적재 주기와
 * 정합성 주기의 어긋남" 자체가 생기지 않게 한다.
 *
 * <p>멱등 — 같은 스냅샷을 다시 적용하면 변경 0건으로 끝난다 (블루-그린 전환기 중복 실행에 안전).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvsStoreSyncService {

  /**
   * 스냅샷의 브랜드별 건수가 기존 마스터 대비 이 비율 미만이면 부분 수집·소프트 차단으로 보고 적용을 중단한다
   * (crawler 적재기의 가드와 동일한 기준).
   */
  private static final double MIN_BRAND_RATIO = 0.8;

  private final CvsStoreRepository cvsStoreRepository;
  private final ShippingAddressRepository shippingAddressRepository;

  /**
   * 마스터 diff(upsert·스테일 삭제)와 배송지 지점명 정합화를 한 트랜잭션으로 적용한다 — 검색 API 는 반쯤 교체된
   * 마스터를 보지 않는다.
   */
  @Transactional
  public CvsStoreSyncResult apply(final CvsStoreSnapshot snapshot) {
    final List<CvsStore> existing = cvsStoreRepository.findAll();

    final Optional<String> guardFailure = checkBrandCounts(snapshot, existing);
    if (guardFailure.isPresent()) {
      log.error("접수처 스냅샷 적용 중단 — {}", guardFailure.get());
      return CvsStoreSyncResult.skipped(guardFailure.get());
    }

    final Map<StoreKey, CvsStore> masterByKey = new HashMap<>();
    for (final CvsStore store : existing) {
      masterByKey.put(new StoreKey(store.getBrand(), store.getStoreCode()), store);
    }

    int inserted = 0;
    int updated = 0;
    final List<CvsStore> newStores = new ArrayList<>();
    final Map<StoreKey, CvsStoreSnapshot.Row> snapshotByKey = new HashMap<>();

    for (final CvsStoreSnapshot.Row row : snapshot.stores()) {
      final StoreKey key = new StoreKey(CvsBrand.valueOf(row.brand()), row.storeCode());
      if (snapshotByKey.putIfAbsent(key, row) != null) {
        continue; // 크롤러가 중복 제거를 끝내지만 방어적으로 첫 항목만 취한다
      }

      final CvsStore master = masterByKey.get(key);
      if (master == null) {
        newStores.add(
            CvsStore.create(
                key.brand(),
                row.storeCode(),
                row.name(),
                row.tel(),
                row.sido(),
                row.address(),
                row.postNo(),
                row.latitude(),
                row.longitude(),
                row.receiveYn(),
                row.pickupYn()));
        inserted++;
      } else if (master.applySnapshot(
          row.name(),
          row.tel(),
          row.sido(),
          row.address(),
          row.postNo(),
          row.latitude(),
          row.longitude(),
          row.receiveYn(),
          row.pickupYn())) {
        updated++;
      }
    }
    cvsStoreRepository.saveAll(newStores);

    final List<Long> staleIds =
        existing.stream()
            .filter(
                store ->
                    !snapshotByKey.containsKey(new StoreKey(store.getBrand(), store.getStoreCode())))
            .map(CvsStore::getId)
            .toList();
    cvsStoreRepository.deleteAllByIds(staleIds);

    // 삭제·개명·신규가 모두 반영된 최종 마스터 기준으로 배송지 지점명을 맞춘다
    // (재개점으로 같은 코드가 되살아난 경우도 폐점 후보가 아니라 정상 연결로 본다).
    final Set<Long> staleIdSet = Set.copyOf(staleIds);
    masterByKey.values().removeIf(store -> staleIdSet.contains(store.getId()));
    for (final CvsStore store : newStores) {
      masterByKey.put(new StoreKey(store.getBrand(), store.getStoreCode()), store);
    }
    final ReconcileResult reconcile = reconcileShippingAddressNames(masterByKey);

    final CvsStoreSyncResult result =
        new CvsStoreSyncResult(
            true,
            inserted,
            updated,
            staleIds.size(),
            reconcile.renamed(),
            reconcile.conflicts(),
            reconcile.closedCandidates(),
            null);
    log.info(
        "접수처 스냅샷 적용 완료 (generatedAt={}) — 신규 {}, 변경 {}, 삭제 {}, 배송지 지점명 갱신 {} (충돌 스킵 {}, 폐점 후보 {})",
        snapshot.generatedAt(),
        inserted,
        updated,
        staleIds.size(),
        reconcile.renamed(),
        reconcile.conflicts(),
        reconcile.closedCandidates());
    return result;
  }

  private static Optional<String> checkBrandCounts(
      final CvsStoreSnapshot snapshot, final List<CvsStore> existing) {
    final Map<CvsBrand, Long> existingCounts = new HashMap<>();
    for (final CvsStore store : existing) {
      existingCounts.merge(store.getBrand(), 1L, Long::sum);
    }
    final Map<CvsBrand, Long> snapshotCounts = new HashMap<>();
    for (final CvsStoreSnapshot.Row row : snapshot.stores()) {
      snapshotCounts.merge(CvsBrand.valueOf(row.brand()), 1L, Long::sum);
    }

    for (final Map.Entry<CvsBrand, Long> entry : existingCounts.entrySet()) {
      final long current = entry.getValue();
      final long incoming = snapshotCounts.getOrDefault(entry.getKey(), 0L);
      if (incoming < current * MIN_BRAND_RATIO) {
        return Optional.of(
            "%s 스냅샷 %d건 < 기존 %d건의 80%% — 수집 이상 의심".formatted(entry.getKey(), incoming, current));
      }
    }
    return Optional.empty();
  }

  /**
   * 배송지 지점명을 마스터 기준으로 따라 옮긴다. 저장 형식(브랜드 라벨 + 공백 + 지점명)은 유지한 채, 라벨을 뗀
   * 실제 지점명이 달라진 경우에만 갱신한다 — 형식 차이("GS25 강남점" vs "GS25강남점")를 개명으로 오판해 전 행을
   * 재작성하는 것을 막고, 등록 중복 검사(지점명 문자열 기반)의 일관성을 지킨다.
   */
  private ReconcileResult reconcileShippingAddressNames(final Map<StoreKey, CvsStore> masterByKey) {
    int renamed = 0;
    int conflicts = 0;
    int closedCandidates = 0;

    for (final ShippingAddress address : shippingAddressRepository.findAllByStoreCodeIsNotNull()) {
      final CvsBrand brand = brandOf(address.getShippingMethod());
      final CvsStore master = masterByKey.get(new StoreKey(brand, address.getStoreCode()));
      if (master == null) {
        closedCandidates++;
        log.info(
            "배송지 {} 의 접수처(brand={}, storeCode={})가 마스터에 없음 — 폐점 후보",
            address.getId(),
            brand,
            address.getStoreCode());
        continue;
      }

      final String label = brand.name();
      final String strippedStored = stripBrandLabel(label, address.getStoreName());
      final String strippedMaster = stripBrandLabel(label, master.getName());
      if (strippedStored.equals(strippedMaster)) {
        continue;
      }

      final String newName = label + " " + strippedMaster;
      // (user, method, store_name) 유니크 제약 — 개명 전/후 이름으로 같은 지점을 둘 다 등록해둔 유저는 스킵.
      if (shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
          address.getUserId(), address.getShippingMethod().name(), newName)) {
        conflicts++;
        log.warn("배송지 {} 지점명 갱신 충돌 — '{}' 이미 존재, 스킵", address.getId(), newName);
        continue;
      }
      address.renameStore(newName);
      renamed++;
    }
    return new ReconcileResult(renamed, conflicts, closedCandidates);
  }

  private static CvsBrand brandOf(final ShippingMethod method) {
    return method == ShippingMethod.GS25_HALF ? CvsBrand.GS25 : CvsBrand.CU;
  }

  /**
   * 지점명 앞의 브랜드 라벨을 뗀다 — 클라이언트 저장 형식("GS25 강남점")과 마스터 형식("GS25강남점")을 같은
   * 기준으로 비교하기 위함. "CUBE점" 처럼 라벨 철자로 시작하는 지점명은 라벨 뒤에 영숫자가 이어지므로 떼지 않는다
   * (클라이언트 stripLeadingConvenienceStoreLabel 과 동일 규칙).
   */
  private static String stripBrandLabel(final String label, final String storeName) {
    final Pattern pattern =
        Pattern.compile("^" + Pattern.quote(label) + "(?![A-Za-z0-9])[\\s-]*");
    String result = storeName.trim();
    String next = pattern.matcher(result).replaceFirst("");
    while (!next.equals(result)) {
      result = next.trim();
      next = pattern.matcher(result).replaceFirst("");
    }
    return result;
  }

  private record StoreKey(CvsBrand brand, String storeCode) {}

  private record ReconcileResult(int renamed, int conflicts, int closedCandidates) {}
}
