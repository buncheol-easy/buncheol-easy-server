package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SocialProvider {
  KAKAO("kakao"),
  // 앱(iOS) 전용 — 웹에는 애플 로그인 입구가 없다. 연령대를 받지 않아 C2C 개최 자격이 생기지 않는다.
  APPLE("apple");

  private final String value;

  public static SocialProvider from(final String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException(ErrorCode.PROVIDER_REQUIRED);
    }
    for (SocialProvider provider : values()) {
      if (provider.name().equals(name)) {
        return provider;
      }
    }
    throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
  }

  public boolean matched(final String value) {
    return this.value.equals(value);
  }
}
