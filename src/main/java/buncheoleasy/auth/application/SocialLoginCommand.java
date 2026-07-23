package buncheoleasy.auth.application;

import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import java.util.List;

/**
 * 소셜 로그인 처리 입력. name·phoneNumber 는 카카오싱크 동의창에서 받은 값(없으면 null — 기존 방식 가입), serviceTerms 는 간편가입 약관
 * 동의 내역(조회 실패·비대상이면 빈 리스트).
 */
public record SocialLoginCommand(
    String provider,
    String providerId,
    String email,
    String name,
    String phoneNumber,
    List<ServiceTermAgreement> serviceTerms) {

  public static SocialLoginCommand of(
      final String provider, final String providerId, final String email) {
    return new SocialLoginCommand(provider, providerId, email, null, null, List.of());
  }
}
