package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.image.BuncheolImage;
import java.util.List;

/**
 * 분철 이미지 1장. id·URL·대표사진 여부를 한 객체로 묶어, 배열 인덱스 정렬 같은 암묵 계약 없이 대표사진을 식별한다.
 *
 * @param id 이미지 id (수정 시 keepImageIds·thumbnailImageId 로 사용)
 * @param url 이미지 URL
 * @param thumbnail 대표사진 여부. 이미지가 1장 이상이면 목록에서 정확히 1장만 true
 */
public record BuncheolImageResponse(Long id, String url, boolean thumbnail) {

  /** 등록 순 이미지 목록을 응답으로 변환한다. 플래그가 유실된 레거시 데이터는 첫 번째 이미지를 대표사진으로 폴백한다. */
  public static List<BuncheolImageResponse> listFrom(final List<BuncheolImage> images) {
    boolean hasFlagged = images.stream().anyMatch(BuncheolImage::isThumbnail);
    return images.stream()
        .map(
            image ->
                new BuncheolImageResponse(
                    image.getId(),
                    image.getImageUrl(),
                    hasFlagged ? image.isThumbnail() : image == images.getFirst()))
        .toList();
  }
}
