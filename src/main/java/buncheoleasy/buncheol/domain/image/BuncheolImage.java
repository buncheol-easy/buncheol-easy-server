package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.domain.CreatedAtEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolImage extends CreatedAtEntity {

  private static final int MAX_IMAGE_URL_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "image_url", nullable = false, length = 500, updatable = false)
  private String imageUrl;

  // 목록 카드에 노출되는 대표사진 여부. 분철당 최대 1장만 true 를 갖는다(플래그가 없으면 조회 쿼리가 MIN(id) 로 폴백).
  @Column(name = "is_thumbnail", nullable = false)
  private boolean thumbnail;

  public static BuncheolImage create(
      final Long buncheolId, final String imageUrl, final boolean thumbnail) {
    return new BuncheolImage(buncheolId, imageUrl, thumbnail);
  }

  private BuncheolImage(final Long buncheolId, final String imageUrl, final boolean thumbnail) {
    validate(buncheolId, imageUrl);
    this.buncheolId = buncheolId;
    this.imageUrl = imageUrl;
    this.thumbnail = thumbnail;
  }

  private void validate(final Long buncheolId, final String imageUrl) {
    validateBuncheolId(buncheolId);
    validateImageUrl(imageUrl);
  }

  private void validateBuncheolId(final Long buncheolId) {
    if (buncheolId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateImageUrl(final String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_URL_REQUIRED);
    }
    if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_URL_LENGTH_INVALID);
    }
  }
}
