package buncheoleasy.buncheol.domain.image;

import java.util.List;

public interface BuncheolImageRepository {

  List<BuncheolImage> saveAll(List<BuncheolImage> buncheolImages);

  void deleteByBuncheolIdExcludingIds(Long buncheolId, List<Long> keepImageIds);

  /** 분철별 가장 먼저 등록된 이미지(MIN(id)) 1장만 일괄 조회. 분철에 이미지가 없으면 결과에서 누락. */
  List<BuncheolImage> findFirstByBuncheolIds(List<Long> buncheolIds);
}
