package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolImage {

  private static final int MAX_IMAGE_URL_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "image_url", nullable = false, length = 500, updatable = false)
  private String imageUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static BuncheolImage create(final Long buncheolId, final String imageUrl) {
    return new BuncheolImage(buncheolId, imageUrl);
  }

  private BuncheolImage(final Long buncheolId, final String imageUrl) {
    validate(buncheolId, imageUrl);
    this.buncheolId = buncheolId;
    this.imageUrl = imageUrl;
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

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}
