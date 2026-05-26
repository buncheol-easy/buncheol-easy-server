package buncheoleasy.buncheol.infrastructure.image;

import buncheoleasy.buncheol.domain.image.BuncheolImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolImageRepository extends JpaRepository<BuncheolImage, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM BuncheolImage b WHERE b.buncheolId = :buncheolId")
  void deleteAllByBuncheolId(@Param("buncheolId") Long buncheolId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "DELETE FROM BuncheolImage b "
          + "WHERE b.buncheolId = :buncheolId AND b.id NOT IN :keepImageIds")
  void deleteByBuncheolIdAndIdNotIn(
      @Param("buncheolId") Long buncheolId, @Param("keepImageIds") List<Long> keepImageIds);

  /** 분철별 MIN(id) 이미지만 한 쿼리로 조회. */
  @Query(
      "SELECT b FROM BuncheolImage b "
          + "WHERE b.id IN ("
          + "  SELECT MIN(b2.id) FROM BuncheolImage b2 "
          + "  WHERE b2.buncheolId IN :buncheolIds "
          + "  GROUP BY b2.buncheolId)")
  List<BuncheolImage> findFirstByBuncheolIds(@Param("buncheolIds") List<Long> buncheolIds);

  List<BuncheolImage> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);
}
