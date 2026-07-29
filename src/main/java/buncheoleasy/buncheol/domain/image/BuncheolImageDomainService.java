package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolImageDomainService {

  private static final int MIN_IMAGE_COUNT = 1;
  private static final int MAX_IMAGE_COUNT = 5;

  private final BuncheolImageRepository buncheolImageRepository;

  /**
   * 이미지를 리스트 순서 그대로 저장한다(id ASC = 업로드 순서). 대표사진은 순서를 바꾸지 않고 {@code thumbnailPosition} 위치의 이미지에 플래그로만
   * 기록한다.
   *
   * @param thumbnailPosition 대표사진으로 지정할 {@code imageUrls} 내 인덱스. null 이면 이번 저장분에 대표사진 없음
   */
  @Transactional
  public void createBuncheolImages(
      final Long buncheolId, final List<String> imageUrls, final Integer thumbnailPosition) {
    if (thumbnailPosition != null) {
      // 커밋 후 비동기 업로드라 그 사이 다른 수정이 대표사진을 지정했을 수 있다 — 삽입 직전 해제를 같은 트랜잭션으로 묶어
      // "분철당 플래그 최대 1장" 을 유지한다.
      buncheolImageRepository.clearThumbnail(buncheolId);
    }
    List<BuncheolImage> newBuncheolImages =
        IntStream.range(0, imageUrls.size())
            .mapToObj(
                i ->
                    BuncheolImage.create(
                        buncheolId,
                        imageUrls.get(i),
                        thumbnailPosition != null && thumbnailPosition == i))
            .toList();

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

  // 대표사진 인덱스는 함께 업로드하는 이미지 목록 범위 안이어야 한다.
  public void validateThumbnailIndex(final int imageCount, final int thumbnailIndex) {
    if (thumbnailIndex < 0 || thumbnailIndex >= imageCount) {
      throw new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID);
    }
  }

  /**
   * 수정 시 대표사진 지정 검증. 기존 이미지 지정({@code thumbnailImageId})과 신규 이미지 지정({@code thumbnailIndex}) 중 정확히 하나가
   * 필수이며, 각각 유지 목록/신규 이미지 범위 안이어야 한다.
   */
  public void validateThumbnailSelection(
      final List<Long> keepImageIds,
      final int newImageCount,
      final Long thumbnailImageId,
      final Integer thumbnailIndex) {
    if (thumbnailImageId == null && thumbnailIndex == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_REQUIRED);
    }
    if (thumbnailImageId != null && thumbnailIndex != null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED);
    }
    if (thumbnailImageId != null && !keepImageIds.contains(thumbnailImageId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_IMAGE_INVALID);
    }
    if (thumbnailIndex != null) {
      validateThumbnailIndex(newImageCount, thumbnailIndex);
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

  /** 기존 이미지 하나를 대표사진으로 교체한다(기존 플래그 해제 후 지정). 두 쿼리가 원자적이어야 하므로 트랜잭션을 명시한다. */
  @Transactional
  public void changeThumbnail(final Long buncheolId, final Long thumbnailImageId) {
    buncheolImageRepository.clearThumbnail(buncheolId);
    if (!buncheolImageRepository.markThumbnail(buncheolId, thumbnailImageId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_THUMBNAIL_IMAGE_INVALID);
    }
  }

  /** 신규 업로드 이미지가 대표사진이 될 예정이라 기존 플래그만 미리 해제한다(플래그 지정은 커밋 후 업로드 리스너가 수행). */
  public void clearThumbnail(final Long buncheolId) {
    buncheolImageRepository.clearThumbnail(buncheolId);
  }
}
