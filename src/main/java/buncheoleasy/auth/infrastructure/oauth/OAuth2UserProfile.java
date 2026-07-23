package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.user.domain.SocialProvider;

/**
 * 소셜 로그인에서 추출한 사용자 프로필. name·phoneNumber 는 카카오싱크 동의창을 거친 경우에만 채워진다(전화번호는 01x 정규화 형식). 값이 없으면 null
 * — 기존 흐름(추가정보 화면 보완)으로 가입된다.
 */
public record OAuth2UserProfile(
    SocialProvider provider, String providerId, String email, String name, String phoneNumber) {

  public OAuth2UserProfile(
      final SocialProvider provider, final String providerId, final String email) {
    this(provider, providerId, email, null, null);
  }

  /** 카카오 API 보강 조회 결과를 병합한다 — 이미 있는 값은 유지한다. */
  public OAuth2UserProfile withUserInfo(final String name, final String phoneNumber) {
    return new OAuth2UserProfile(
        provider,
        providerId,
        email,
        this.name != null ? this.name : name,
        this.phoneNumber != null ? this.phoneNumber : phoneNumber);
  }
}
