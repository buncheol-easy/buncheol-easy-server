package buncheoleasy.admin.dto.response;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRole;

/** 현재 로그인한 관리자 본인 정보. admin 프론트의 세션 확인(로그인 유지 게이트)용. */
public record AdminMeResponse(String loginId, AdminRole role) {

  public static AdminMeResponse from(final Admin admin) {
    return new AdminMeResponse(admin.getLoginId(), admin.getRole());
  }
}
