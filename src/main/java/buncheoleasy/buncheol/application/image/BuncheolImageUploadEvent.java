package buncheoleasy.buncheol.application.image;

import java.util.List;

/**
 * @param thumbnailIndex 대표사진으로 지정할 {@code images} 내 인덱스(0-base). null 이면 이번 업로드분에서 대표사진을 지정하지 않는다(수정
 *     시 기존 이미지를 대표로 유지하는 경우).
 * @param initialUpload 개최 직후 첫 업로드인지(true 면 기존 대표사진 해제 없이 저장).
 */
public record BuncheolImageUploadEvent(
    Long buncheolId, List<ImageFile> images, Integer thumbnailIndex, boolean initialUpload) {

  public static BuncheolImageUploadEvent ofHold(
      final Long buncheolId, final List<ImageFile> images, final Integer thumbnailIndex) {
    return new BuncheolImageUploadEvent(buncheolId, images, thumbnailIndex, true);
  }

  public static BuncheolImageUploadEvent ofModify(
      final Long buncheolId, final List<ImageFile> images, final Integer thumbnailIndex) {
    return new BuncheolImageUploadEvent(buncheolId, images, thumbnailIndex, false);
  }
}
