package buncheoleasy.buncheol.infrastructure.image;

import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolImageRepositoryAdapter implements BuncheolImageRepository {

  private final JpaBuncheolImageRepository jpaBuncheolImageRepository;

  @Override
  public List<BuncheolImage> saveAll(List<BuncheolImage> buncheolImages) {
    if (buncheolImages.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolImageRepository.saveAll(buncheolImages);
  }

  @Override
  public void deleteByBuncheolIdExcludingIds(Long buncheolId, List<Long> keepImageIds) {
    if (keepImageIds == null || keepImageIds.isEmpty()) {
      jpaBuncheolImageRepository.deleteAllByBuncheolId(buncheolId);
      return;
    }
    jpaBuncheolImageRepository.deleteByBuncheolIdAndIdNotIn(buncheolId, keepImageIds);
  }

  @Override
  public List<BuncheolImage> findThumbnailsByBuncheolIds(List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolImageRepository.findThumbnailsByBuncheolIds(buncheolIds);
  }

  @Override
  public List<BuncheolImage> findAllByBuncheolIdOrderByIdAsc(Long buncheolId) {
    return jpaBuncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);
  }

  @Override
  public void clearThumbnail(Long buncheolId) {
    jpaBuncheolImageRepository.clearThumbnail(buncheolId);
  }

  @Override
  public boolean markThumbnail(Long buncheolId, Long imageId) {
    return jpaBuncheolImageRepository.markThumbnail(buncheolId, imageId) > 0;
  }
}
