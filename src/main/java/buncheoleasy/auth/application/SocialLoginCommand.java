package buncheoleasy.auth.application;

import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import java.util.List;

/**
 * 소셜 로그인 처리 입력. name·phoneNumber·ageRange 는 카카오싱크 동의창에서 받은 값(없으면 null — 기존 방식 가입, 연령대는 선택 동의),
 * ageRangeWithdrawn 은 카카오가 연령대 미동의/철회를 확정 신호로 알린 경우 true(저장값 파기), serviceTerms 는 간편가입 약관 동의 내역(조회
 * 실패·비대상이면 빈 리스트).
 */
public record SocialLoginCommand(
    String provider,
    String providerId,
    String email,
    String name,
    String phoneNumber,
    String ageRange,
    boolean ageRangeWithdrawn,
    List<ServiceTermAgreement> serviceTerms) {

  public static SocialLoginCommand of(
      final String provider, final String providerId, final String email) {
    return new SocialLoginCommand(provider, providerId, email, null, null, null, false, List.of());
  }
}
