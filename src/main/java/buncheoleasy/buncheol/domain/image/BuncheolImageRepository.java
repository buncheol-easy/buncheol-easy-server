package buncheoleasy.buncheol.domain.image;

import java.util.List;

public interface BuncheolImageRepository {

  List<BuncheolImage> saveAll(List<BuncheolImage> buncheolImages);

  void deleteByBuncheolIdExcludingIds(Long buncheolId, List<Long> keepImageIds);

  /**
   * 분철별 대표사진(is_thumbnail) 1장만 일괄 조회. 플래그가 없는 분철은 가장 먼저 등록된 이미지(MIN(id))로 폴백. 분철에 이미지가 없으면 결과에서 누락.
   */
  List<BuncheolImage> findThumbnailsByBuncheolIds(List<Long> buncheolIds);

  /** 단일 분철의 이미지 전체를 등록 순(id ASC)으로 조회. */
  List<BuncheolImage> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  /** 분철의 기존 대표사진 플래그를 모두 해제한다. */
  void clearThumbnail(Long buncheolId);

  /** 해당 분철 소유의 이미지 1장을 대표사진으로 지정한다. 소유가 아니면 false. */
  boolean markThumbnail(Long buncheolId, Long imageId);
}
