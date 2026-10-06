package buncheoleasy.user.domain;

/**
 * C2C 개최 자격 판정 결과 (docs/46 §7.1-8 · docs/50). 제출 시점 게이트({@link
 * UserDomainService#requireC2cHostQualification})와 사전 조회(개최 폼 진입 차단 — docs/53 Q-07)가 같은 판정을 공유하도록
 * 예외 대신 값으로 표현한다.
 *
 * <p>판정 → 표현(에러코드·응답 사유) 변환은 호출부에 둔다 — 이 enum 이 에러코드를 알면 "충족했을 때는 호출하면 안 되는" 부분 함수가 생긴다.
 */
public enum C2cHostQualification {
  QUALIFIED,
  // 가입 미완료(전화번호 미보유) — C2C 직거래는 개최자 연락처가 분쟁 처리의 근거다.
  PHONE_REQUIRED,
  // 연령대 미보유 — 카카오 재동의로 회복 가능.
  AGE_UNVERIFIED,
  // 미성년 확정 — 회복 경로 없음.
  NOT_ADULT;

  public boolean isQualified() {
    return this == QUALIFIED;
  }
}
