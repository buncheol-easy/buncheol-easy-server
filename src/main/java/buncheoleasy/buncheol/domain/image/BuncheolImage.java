package buncheoleasy.buncheol.domain.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class BuncheolImage {

    private static final int MAX_IMAGE_URL_LENGTH = 500;

    private Long id;
    private final Long buncheolId;
    private final String imageUrl;
    private LocalDateTime createdAt;

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
}
