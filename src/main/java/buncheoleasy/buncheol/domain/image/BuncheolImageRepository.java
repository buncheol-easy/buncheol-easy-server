package buncheoleasy.buncheol.domain.image;

import java.util.List;

public interface BuncheolImageRepository {

  List<BuncheolImage> saveAll(List<BuncheolImage> buncheolImages);

  void deleteByBuncheolIdExcludingIds(Long buncheolId, List<Long> keepImageIds);
}
