package buncheoleasy.cvsstore.application.sync;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.user.domain.shipping.ShippingAddress;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CvsStoreSyncService 테스트")
class CvsStoreSyncServiceTest {

  @Autowired private CvsStoreSyncService cvsStoreSyncService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "sync_user");
  }

  private CvsStore persistStore(final CvsBrand brand, final String storeCode, final String name) {
    CvsStore store =
        CvsStore.create(
            brand,
            storeCode,
            name,
            "021234567",
            "서울",
            "서울 강남구 테헤란로 1",
            "06236",
            new BigDecimal("37.5010000"),
            new BigDecimal("127.0390000"),
            true,
            true);
    em.persist(store);
    return store;
  }

  private ShippingAddress persistShippingAddress(
      final String method, final String storeName, final String storeCode) {
    ShippingAddress address =
        ShippingAddress.create(userId, method, storeName, storeCode, null, false);
    em.persist(address);
    return address;
  }

  private static CvsStoreSnapshot.Row row(
      final String brand, final String storeCode, final String name) {
    return new CvsStoreSnapshot.Row(
        brand,
        storeCode,
        name,
        "021234567",
        "서울",
        "서울 강남구 테헤란로 1",
        "06236",
        new BigDecimal("37.5010000"),
        new BigDecimal("127.0390000"),
        true,
        true);
  }

  private static CvsStoreSnapshot snapshot(final CvsStoreSnapshot.Row... rows) {
    return new CvsStoreSnapshot("2026-08-05", List.of(rows));
  }

  private List<CvsStore> findAllStores() {
    em.flush();
    em.clear();
    return em.createQuery("SELECT s FROM CvsStore s ORDER BY s.id", CvsStore.class)
        .getResultList();
  }

  @Nested
  @DisplayName("마스터 diff 적용 테스트")
  class ApplyTest {

    @Test
    void 신규는_추가되고_변경은_갱신되고_스냅샷에_없는_행은_삭제된다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      persistStore(CvsBrand.GS25, "V0002", "GS25폐점점");
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(
              snapshot(
                  row("GS25", "V0001", "GS25뉴강남점"), // 개명
                  row("GS25", "V0003", "GS25신규점"))); // 신규 (V0002 는 삭제)

      assertThat(result.applied()).isTrue();
      assertThat(result.inserted()).isEqualTo(1);
      assertThat(result.updated()).isEqualTo(1);
      assertThat(result.deleted()).isEqualTo(1);
      assertThat(findAllStores())
          .extracting(CvsStore::getName)
          .containsExactly("GS25뉴강남점", "GS25신규점");
    }

    @Test
    void 같은_스냅샷을_다시_적용하면_변경이_없다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      em.flush();
      CvsStoreSnapshot same = snapshot(row("GS25", "V0001", "GS25강남점"));

      CvsStoreSyncResult result = cvsStoreSyncService.apply(same);

      assertThat(result.applied()).isTrue();
      assertThat(result.inserted()).isZero();
      assertThat(result.updated()).isZero();
      assertThat(result.deleted()).isZero();
    }

    @Test
    void 브랜드_건수가_기존의_80퍼센트_미만이면_적용을_중단한다() {
      for (int i = 1; i <= 10; i++) {
        persistStore(CvsBrand.GS25, "V" + i, "GS25지점" + i);
      }
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(snapshot(row("GS25", "V1", "GS25지점1")));

      assertThat(result.applied()).isFalse();
      assertThat(result.skipReason()).contains("GS25");
      assertThat(findAllStores()).hasSize(10);
    }
  }

  @Nested
  @DisplayName("배송지 지점명 정합화 테스트")
  class ReconcileTest {

    @Test
    void 마스터_지점명이_바뀌면_배송지_지점명을_저장_형식대로_따라_옮긴다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      ShippingAddress address = persistShippingAddress("GS25_HALF", "GS25 강남점", "V0001");
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(snapshot(row("GS25", "V0001", "GS25뉴강남점")));

      assertThat(result.renamed()).isEqualTo(1);
      em.flush();
      em.clear();
      assertThat(em.find(ShippingAddress.class, address.getId()).getStoreName())
          .isEqualTo("GS25 뉴강남점");
    }

    @Test
    void 형식만_다른_지점명은_개명으로_보지_않는다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      persistShippingAddress("GS25_HALF", "GS25 강남점", "V0001"); // 라벨+공백 형식
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(snapshot(row("GS25", "V0001", "GS25강남점")));

      assertThat(result.renamed()).isZero();
    }

    @Test
    void 새_지점명이_이미_등록돼_있으면_유니크_충돌이라_스킵한다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      persistShippingAddress("GS25_HALF", "GS25 강남점", "V0001");
      persistShippingAddress("GS25_HALF", "GS25 뉴강남점", null); // 개명 후 이름을 이미 보유
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(snapshot(row("GS25", "V0001", "GS25뉴강남점")));

      assertThat(result.renamed()).isZero();
      assertThat(result.renameConflicts()).isEqualTo(1);
    }

    @Test
    void 마스터에_없는_코드의_배송지는_폐점_후보로_집계만_한다() {
      persistStore(CvsBrand.GS25, "V0001", "GS25강남점");
      ShippingAddress address = persistShippingAddress("GS25_HALF", "GS25 사라진점", "V9999");
      em.flush();

      CvsStoreSyncResult result =
          cvsStoreSyncService.apply(snapshot(row("GS25", "V0001", "GS25강남점")));

      assertThat(result.closedCandidates()).isEqualTo(1);
      em.flush();
      em.clear();
      assertThat(em.find(ShippingAddress.class, address.getId()).getStoreName())
          .isEqualTo("GS25 사라진점");
    }
  }
}
