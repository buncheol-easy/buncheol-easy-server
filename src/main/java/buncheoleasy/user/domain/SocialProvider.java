package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SocialProvider {

    KAKAO("kakao");

    private final String value;

    public static SocialProvider from(final String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PROVIDER_REQUIRED);
        }
        for (SocialProvider provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
    }

    public boolean matched(final String value) {
        return this.value.equals(value);
    }
}
