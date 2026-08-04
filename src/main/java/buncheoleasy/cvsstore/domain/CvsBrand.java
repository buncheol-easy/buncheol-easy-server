package buncheoleasy.cvsstore.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;

/** 택배 접수처 편의점 브랜드. {@code cvs_stores.brand} 컬럼 값과 1:1 이다. */
public enum CvsBrand {
  GS25,
  CU;

  /** {@code null}/공백은 미지정으로 보고 {@code null} 을 돌려준다. 대소문자 무관, 그 외 값은 {@code CVS-001}. */
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
