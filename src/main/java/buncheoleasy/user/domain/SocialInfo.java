package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.regex.Pattern;

@Embeddable
public record SocialInfo(
    @Enumerated(EnumType.STRING) @Column(name = "provider", nullable = false, length = 20)
        SocialProvider provider,
    @Column(name = "provider_id", nullable = false, length = 100) String providerId) {

  private static final Pattern PROVIDER_ID_REGEX = Pattern.compile("^[a-zA-Z0-9_-]+$");
  private static final int MAX_LENGTH = 100;

  public SocialInfo {
    validateProviderId(providerId);
  }

  public static SocialInfo of(final String provider, final String providerId) {
    return new SocialInfo(SocialProvider.from(provider), providerId);
  }

  private void validateProviderId(final String providerId) {
    if (providerId == null || providerId.isBlank()) {
      throw new BusinessException(ErrorCode.USER_SOCIAL_ID_REQUIRED);
    }
    if (providerId.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_SOCIAL_ID_LENGTH_INVALID);
    }
    if (!PROVIDER_ID_REGEX.matcher(providerId).matches()) {
      throw new BusinessException(ErrorCode.USER_SOCIAL_ID_FORMAT_INVALID);
    }
  }
}
