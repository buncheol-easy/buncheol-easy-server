package buncheoleasy.cvsstore.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;

/** 택배 접수처 편의점 브랜드. {@code cvs_stores.brand} 컬럼 값과 1:1 이다. */
public enum CvsBrand {
  GS25,
  CU;

  /**
   * 브랜드 문자열을 해석한다. {@code null}/공백은 "미지정"으로 보고 {@code null} 을 돌려준다. 사이블링의 직접 enum 바인딩과 달리
   * String 경유인 이유: 대소문자를 허용하고, 잘못된 값에 generic 400 대신 전용 코드({@code CVS-001})를 주기 위함.
   */
  public static CvsBrand fromFilter(final String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return CvsBrand.valueOf(name.trim().toUpperCase());
    } catch (final IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.CVS_BRAND_INVALID, ex);
    }
  }
}
