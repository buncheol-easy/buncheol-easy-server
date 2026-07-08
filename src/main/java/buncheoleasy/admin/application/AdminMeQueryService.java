package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminMeResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 본인 확인. 토큰 claim 은 발급 시점 스냅샷이므로 admins 테이블을 재조회해 확인 시점 기준으로 판정한다 — 계정 삭제 후에도 토큰 수명 동안 게이트를
 * 통과하는 창을 admin 프론트 진입 시점에 한 번 더 좁힌다.
 */
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
