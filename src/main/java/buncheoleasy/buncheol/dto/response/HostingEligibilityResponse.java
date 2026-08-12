package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.C2cHostQualification;

/**
 * 개최 자격 사전 조회 응답 (docs/53 Q-07). 개최 폼을 다 채운 뒤 제출 시점에야 자격 실패가 드러나던 것을 진입 시점에 판정하기 위한 API 다.
 *
 * <p>{@code eligible} 이 true 면 {@code reason} 은 null 이다. 판정은 제출 게이트({@code
 * BuncheolService#resolveHostFlowType})와 같은 로직을 공유하지만 조회 시점 스냅샷이므로, 최종 차단은 여전히 제출 시점 게이트가 한다.
 */
public record HostingEligibilityResponse(boolean eligible, Reason reason) {

  public enum Reason {
    /** 가입 미완료 — 전화번호 등록 필요. */
    PHONE_REQUIRED,
    /** 연령대 미보유 — 카카오 재로그인·재동의로 회복 가능. */
    AGE_UNVERIFIED,
    /** 미성년 확정 — 개최 불가. */
    NOT_ADULT,
    /** 활성(모집중·입금 수집중) 개최 수 상한 초과. */
    LIMIT_EXCEEDED,
    /** 정산 계좌 미등록 — LEGACY·C2C 공통이라 운영진에게도 적용된다. */
    BANK_ACCOUNT_REQUIRED
  }

  public static HostingEligibilityResponse allowed() {
    return new HostingEligibilityResponse(true, null);
  }

  public static HostingEligibilityResponse blocked(final Reason reason) {
    return new HostingEligibilityResponse(false, reason);
  }

  public static HostingEligibilityResponse from(final C2cHostQualification qualification) {
    return switch (qualification) {
      case QUALIFIED -> allowed();
      case PHONE_REQUIRED -> blocked(Reason.PHONE_REQUIRED);
      case AGE_UNVERIFIED -> blocked(Reason.AGE_UNVERIFIED);
      case NOT_ADULT -> blocked(Reason.NOT_ADULT);
    };
  }
}
