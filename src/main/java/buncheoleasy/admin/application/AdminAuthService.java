package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 ID/PW 로그인. 아이디 없음과 비밀번호 불일치를 같은 에러({@link ErrorCode#ADMIN_LOGIN_FAILED})로 응답하고, 아이디가 없어도
 * 더미 해시와 BCrypt 비교를 수행해 응답 시간을 맞춘다 — 에러 메시지·타이밍 어느 쪽으로도 계정 존재 여부를 열거할 수 없게 한다. 실패는 감사 로그로 남긴다
 * (저속 크리덴셜 스터핑 탐지용). 성공 시 role claim 이 실린 관리자 access token 을 발급한다 (refresh 없음 — 만료 시 재로그인).
 */
@Slf4j
@Service
public class AdminAuthService {

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  // 아이디 없음 경로에서도 실제 해시 비교와 같은 시간이 걸리도록 기동 시 만들어 두는 더미 해시.
  private final String enumerationGuardHash;

  public AdminAuthService(
      final AdminRepository adminRepository,
      final PasswordEncoder passwordEncoder,
      final JwtTokenProvider jwtTokenProvider) {
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.enumerationGuardHash = passwordEncoder.encode("enumeration-timing-guard");
  }

  @Transactional(readOnly = true)
  public AdminLoginResponse login(final String loginId, final String rawPassword) {
    Admin admin = adminRepository.findByLoginId(loginId).orElse(null);

    String storedHash = admin != null ? admin.getPassword() : enumerationGuardHash;
    boolean matched = passwordEncoder.matches(rawPassword, storedHash);

    if (admin == null || !matched) {
      log.warn("관리자 로그인 실패. loginId={}", loginId);
      throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
    }

    return new AdminLoginResponse(jwtTokenProvider.createAdminAccessToken(admin.getId()));
  }
}
