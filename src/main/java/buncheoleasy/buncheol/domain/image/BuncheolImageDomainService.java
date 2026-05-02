package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolImageDomainService {

  private static final int MAX_IMAGE_COUNT = 5;

  private final BuncheolImageRepository buncheolImageRepository;

  public void createBuncheolImages(final Long buncheolId, final List<String> imageUrls) {
    List<BuncheolImage> newBuncheolImages =
        imageUrls.stream().map(url -> BuncheolImage.create(buncheolId, url)).toList();

    buncheolImageRepository.saveAll(newBuncheolImages);
  }

  public void validateImageCount(final int count) {
    if (count > MAX_IMAGE_COUNT) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }
  }

  public void deleteImagesExcluding(final Long buncheolId, final List<Long> keepImageIds) {
    buncheolImageRepository.deleteByBuncheolIdExcludingIds(buncheolId, keepImageIds);
  }
}
