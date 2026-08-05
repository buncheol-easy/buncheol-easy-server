package buncheoleasy.cvsstore.infrastructure;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaCvsStoreRepository extends JpaRepository<CvsStore, Long> {

  /**
   * 지점명 일치 그룹({@code keyword} 가 {@code null} 이면 전체)을 {@code id ASC} 로 검색한다.
   *
   * <p>LIKE 컬럼을 함수로 감싸지 않는다 — {@code utf8mb4_unicode_ci} 가 이미 대소문자 무시 매칭이라 {@code LOWER()} 는
   * 행마다 변환 비용만 얹는다. ESCAPE 절은 Java 리터럴 {@code "ESCAPE '\\'"} 로 작성한다.
   */
  @Query(
      "SELECT s FROM CvsStore s "
          + "WHERE s.pickupYn = true "
          + "  AND (:brand IS NULL OR s.brand = :brand) "
          + "  AND (:keyword IS NULL OR s.name LIKE CONCAT('%', :keyword, '%') ESCAPE '\\') "
          + "  AND (:cursorId IS NULL OR s.id > :cursorId) "
          + "ORDER BY s.id ASC")
  List<CvsStore> searchByName(
      @Param("brand") CvsBrand brand,
      @Param("keyword") String keyword,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /** 주소만 일치 그룹(주소는 매치, 지점명은 불일치)을 {@code id ASC} 로 검색한다. {@code keyword} 필수. */
  @Query(
      "SELECT s FROM CvsStore s "
          + "WHERE s.pickupYn = true "
          + "  AND (:brand IS NULL OR s.brand = :brand) "
          + "  AND s.address LIKE CONCAT('%', :keyword, '%') ESCAPE '\\' "
          + "  AND s.name NOT LIKE CONCAT('%', :keyword, '%') ESCAPE '\\' "
          + "  AND (:cursorId IS NULL OR s.id > :cursorId) "
          + "ORDER BY s.id ASC")
  List<CvsStore> searchByAddressOnly(
      @Param("brand") CvsBrand brand,
      @Param("keyword") String keyword,
      @Param("cursorId") Long cursorId,
      Pageable pageable);
}
