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

  /** 분철별 대표사진 1장만 한 쿼리로 조회. is_thumbnail 이 있으면 그 이미지, 없으면 MIN(id) 폴백. */
  @Query(
      "SELECT b FROM BuncheolImage b "
          + "WHERE b.id IN ("
          + "  SELECT COALESCE("
          + "    MIN(CASE WHEN b2.thumbnail = TRUE THEN b2.id END), MIN(b2.id)) "
          + "  FROM BuncheolImage b2 "
          + "  WHERE b2.buncheolId IN :buncheolIds "
          + "  GROUP BY b2.buncheolId)")
  List<BuncheolImage> findThumbnailsByBuncheolIds(@Param("buncheolIds") List<Long> buncheolIds);

  List<BuncheolImage> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE BuncheolImage b SET b.thumbnail = FALSE "
          + "WHERE b.buncheolId = :buncheolId AND b.thumbnail = TRUE")
  void clearThumbnail(@Param("buncheolId") Long buncheolId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE BuncheolImage b SET b.thumbnail = TRUE "
          + "WHERE b.id = :imageId AND b.buncheolId = :buncheolId")
  int markThumbnail(@Param("buncheolId") Long buncheolId, @Param("imageId") Long imageId);
}
