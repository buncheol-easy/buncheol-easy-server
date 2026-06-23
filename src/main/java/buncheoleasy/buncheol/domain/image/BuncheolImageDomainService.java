package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolImageDomainService {

  private static final int MIN_IMAGE_COUNT = 1;
  private static final int MAX_IMAGE_COUNT = 5;

  private final BuncheolImageRepository buncheolImageRepository;

  public void createBuncheolImages(final Long buncheolId, final List<String> imageUrls) {
    List<BuncheolImage> newBuncheolImages =
        imageUrls.stream().map(url -> BuncheolImage.create(buncheolId, url)).toList();

    buncheolImageRepository.saveAll(newBuncheolImages);
  }

  // 분철은 이미지 최소 1장 이상, 최대 5장. 개최(hold)는 신규 이미지 수로 호출한다.
  public void validateImageCount(final int count) {
    if (count < MIN_IMAGE_COUNT) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_REQUIRED);
    }
    if (count > MAX_IMAGE_COUNT) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }
  }

  // 수정(modify) 시 검증: keepImageIds 는 반드시 해당 분철의 실제 이미지여야 한다(아니면 BUNCHEOL_KEEP_IMAGE_INVALID).
  // 그 뒤 "수정 후 실제 잔존 이미지 수 = 유효한 유지분(중복 제거) + 신규 이미지" 로 최소 1장·최대 5장을 검증한다.
  // keepImageIds 의 개수만 단순 합산하면 존재하지 않는/중복 id 로 최소 1장 불변식을 우회할 수 있어 실제 소유 이미지로 환산한다.
  public void validateModifyImageCount(
      final Long buncheolId, final List<Long> keepImageIds, final int newImageCount) {
    Set<Long> existingImageIds =
        buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId).stream()
            .map(BuncheolImage::getId)
            .collect(Collectors.toSet());

    Set<Long> distinctKeepIds = new HashSet<>(keepImageIds);
    if (!existingImageIds.containsAll(distinctKeepIds)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_KEEP_IMAGE_INVALID);
    }

    validateImageCount(distinctKeepIds.size() + newImageCount);
  }

  public void deleteImagesExcluding(final Long buncheolId, final List<Long> keepImageIds) {
    buncheolImageRepository.deleteByBuncheolIdExcludingIds(buncheolId, keepImageIds);
  }
}
