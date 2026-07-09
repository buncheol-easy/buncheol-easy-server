package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminMeResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 본인 확인. admin 프론트가 진입/새로고침 시 로그인 유지 여부를 판정하는 세션 게이트용. */
@Service
@RequiredArgsConstructor
public class AdminMeQueryService {

  private final AdminRepository adminRepository;

  @Transactional(readOnly = true)
  public AdminMeResponse getMe(final Long adminId) {
    return adminRepository
        .findById(adminId)
        .map(AdminMeResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
  }
}
