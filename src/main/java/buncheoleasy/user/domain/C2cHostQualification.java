package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.ErrorCode;

/**
 * C2C 개최 자격 판정 결과 (docs/46 §7.1-8 · docs/50). 제출 시점 게이트({@link
 * UserDomainService#requireC2cHostQualification})와 사전 조회(개최 폼 진입 차단 — docs/53 Q-07)가 같은 판정을 공유하도록
 * 예외 대신 값으로 표현한다. 실패 사유별 에러코드는 게이트가 던질 때만 쓰인다.
 */
public enum C2cHostQualification {
  QUALIFIED(null),
  // 가입 미완료(전화번호 미보유) — C2C 직거래는 개최자 연락처가 분쟁 처리의 근거다.
  PHONE_REQUIRED(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE),
  // 연령대 미보유 — 카카오 재동의로 회복 가능.
  AGE_UNVERIFIED(ErrorCode.USER_AGE_NOT_VERIFIED),
  // 미성년 확정 — 회복 경로 없음.
  NOT_ADULT(ErrorCode.USER_NOT_ADULT);

  private final ErrorCode errorCode;

  C2cHostQualification(final ErrorCode errorCode) {
    this.errorCode = errorCode;
  }

  public boolean isQualified() {
    return this == QUALIFIED;
  }

  /** 자격 미달 사유의 에러코드. {@link #QUALIFIED} 는 던질 것이 없어 호출하면 예외다. */
  public ErrorCode errorCode() {
    if (errorCode == null) {
      throw new IllegalStateException("자격을 충족한 판정에는 에러코드가 없습니다.");
    }
    return errorCode;
  }
}
