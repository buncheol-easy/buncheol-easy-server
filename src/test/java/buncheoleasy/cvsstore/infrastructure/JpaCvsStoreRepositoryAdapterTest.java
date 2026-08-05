package buncheoleasy.cvsstore.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.cvsstore.domain.CvsStoreCursor;
import buncheoleasy.cvsstore.domain.CvsStoreRepository;
import buncheoleasy.cvsstore.domain.RankedCvsStore;
import buncheoleasy.global.query.LikeEscaper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaCvsStoreRepositoryAdapter 테스트")
class JpaCvsStoreRepositoryAdapterTest {

  @Autowired private CvsStoreRepository cvsStoreRepository;

  @PersistenceContext private EntityManager em;

  private Long gsGangnamId;
  private Long cuGangnamId;
  private Long gsHongdaeId;
  private Long cuPickupOnlyId;
  private Long gsTeheranId;

  @BeforeEach
  void setUp() {
    gsGangnamId = persistStore(CvsBrand.GS25, "V0001", "GS25강남점", "서울 강남구 테헤란로 1", true, true);
    cuGangnamId = persistStore(CvsBrand.CU, "10001", "CU강남타운점", "서울 강남구 역삼로 2", true, true);
    gsHongdaeId = persistStore(CvsBrand.GS25, "V0002", "GS25홍대점", "서울 마포구 양화로 3", false, true);
    cuPickupOnlyId = persistStore(CvsBrand.CU, "10002", "CU접수전용점", "서울 송파구 올림픽로 4", true, false);
    // 지점명엔 키워드가 없고 주소로만 "강남" 이 매치되는 매장 — 주소 그룹 정렬 검증용
    gsTeheranId = persistStore(CvsBrand.GS25, "V0003", "GS25테헤란점", "서울 강남구 강남대로 5", true, true);
  }

  private Long persistStore(
      final CvsBrand brand,
      final String storeCode,
      final String name,
      final String address,
      final boolean receiveYn,
      final boolean pickupYn) {
    CvsStore store =
        CvsStore.create(
            brand,
            storeCode,
            name,
            "021234567",
            "서울",
            address,
            "06236",
            new BigDecimal("37.5010000"),
            new BigDecimal("127.0390000"),
            receiveYn,
            pickupYn);
    em.persist(store);
    em.flush();
    em.clear();
    return store.getId();
  }

  @Nested
  @DisplayName("필터 테스트")
  class FilterTest {

    @Test
    void 픽업_불가_점포는_조회되지_않는다() {
      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(null, null, CvsStoreCursor.firstPage(), 10);

      assertThat(result).extracting(hit -> hit.store().getId()).doesNotContain(cuPickupOnlyId);
      assertThat(result).hasSize(4);
    }

    @Test
    void 브랜드_필터를_적용하면_해당_브랜드만_조회된다() {
      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(
              CvsBrand.GS25, null, CvsStoreCursor.firstPage(), 10);

      assertThat(result)
          .extracting(hit -> hit.store().getId())
          .containsExactly(gsGangnamId, gsHongdaeId, gsTeheranId);
    }

    @Test
    void 키워드는_지점명_일치를_주소_일치보다_먼저_노출한다() {
      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(null, "강남", CvsStoreCursor.firstPage(), 10);

      // 지점명 일치(강남점·강남타운점) 그룹이 먼저, 주소만 일치(테헤란점)가 뒤
      assertThat(result)
          .extracting(hit -> hit.store().getId())
          .containsExactly(gsGangnamId, cuGangnamId, gsTeheranId);
      assertThat(result)
          .extracting(RankedCvsStore::groupRank)
          .containsExactly(
              CvsStoreCursor.RANK_NAME, CvsStoreCursor.RANK_NAME, CvsStoreCursor.RANK_ADDRESS);
    }

    @Test
    void 주소로만_검색해도_조회된다() {
      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(null, "마포구", CvsStoreCursor.firstPage(), 10);

      assertThat(result).extracting(hit -> hit.store().getId()).containsExactly(gsHongdaeId);
    }

    @Test
    void 이스케이프된_와일드카드_문자는_리터럴로_매칭된다() {
      persistStore(CvsBrand.GS25, "V0004", "GS25백프로점100%", "서울 중구 명동길 5", true, true);

      List<RankedCvsStore> escaped =
          cvsStoreRepository.searchPickupStores(
              null, LikeEscaper.escape("100%"), CvsStoreCursor.firstPage(), 10);

      assertThat(escaped).extracting(hit -> hit.store().getName()).containsExactly("GS25백프로점100%");
    }
  }

  @Nested
  @DisplayName("커서 페이지네이션 테스트")
  class CursorPaginationTest {

    @Test
    void 그룹_내에서는_id_오름차순으로_limit_만큼만_조회된다() {
      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(null, null, CvsStoreCursor.firstPage(), 2);

      assertThat(result).extracting(hit -> hit.store().getId()).containsExactly(gsGangnamId, cuGangnamId);
    }

    @Test
    void 지점명_그룹_커서_이후를_조회하면_남은_지점명_그룹과_주소_그룹이_이어진다() {
      CvsStoreCursor cursor = new CvsStoreCursor(CvsStoreCursor.RANK_NAME, gsGangnamId);

      List<RankedCvsStore> result = cvsStoreRepository.searchPickupStores(null, "강남", cursor, 10);

      assertThat(result).extracting(hit -> hit.store().getId()).containsExactly(cuGangnamId, gsTeheranId);
    }

    @Test
    void 주소_그룹_커서_이후를_조회하면_지점명_그룹은_다시_나오지_않는다() {
      // 주소 그룹에 매장 추가: 테헤란점(id 순 앞) → 신규(뒤)
      Long lateAddressMatchId =
          persistStore(CvsBrand.CU, "10003", "CU역삼중앙점", "서울 강남구 강남대로 7", true, true);
      CvsStoreCursor cursor = new CvsStoreCursor(CvsStoreCursor.RANK_ADDRESS, gsTeheranId);

      List<RankedCvsStore> result = cvsStoreRepository.searchPickupStores(null, "강남", cursor, 10);

      assertThat(result).extracting(hit -> hit.store().getId()).containsExactly(lateAddressMatchId);
    }

    @Test
    void 브랜드와_키워드와_커서를_동시에_적용해도_조건이_모두_유지된다() {
      // GS25 + "강남": 지점명 그룹 [강남점], 주소 그룹 [테헤란점]
      CvsStoreCursor cursor = new CvsStoreCursor(CvsStoreCursor.RANK_NAME, gsGangnamId);

      List<RankedCvsStore> result =
          cvsStoreRepository.searchPickupStores(CvsBrand.GS25, "강남", cursor, 10);

      // 지점명 그룹의 CU강남타운점은 브랜드 필터로, 커서 이전 GS25강남점은 keyset 으로 제외
      assertThat(result).extracting(hit -> hit.store().getId()).containsExactly(gsTeheranId);
    }
  }
}
