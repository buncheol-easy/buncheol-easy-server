package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.admin.infrastructure.AdminLoginRateLimitProperties;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.ratelimit.FixedWindowRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 ID/PW 로그인. 아이디 없음과 비밀번호 불일치를 같은 에러({@link ErrorCode#ADMIN_LOGIN_FAILED})로 응답하고, 아이디가 없어도
 * 더미 해시와 BCrypt 비교를 수행해 응답 시간을 맞춘다 — 에러 메시지·타이밍 어느 쪽으로도 계정 존재 여부를 열거할 수 없게 한다. 실패는 감사 로그로 남긴다
 * (저속 크리덴셜 스터핑 탐지용). 성공 시 role claim 이 실린 관리자 access token 을 발급한다 (refresh 없음 — 만료 시 재로그인).
 *
 * <p>여기에 더해 <b>호출 횟수 제한</b>을 둔다. 위 방어들은 "어느 계정이 있는지"를 감추지만 추측 자체의 속도는 줄이지 못하는데, 관리자 토큰은 12시간
 * 유효하고 {@code /v1/admin/**} 전체 — 임의 참여의 입금확인, 배송비 환급 승인/반려 — 에 접근하므로 무제한 온라인 추측을 허용할 수 없다.
 */
@Slf4j
@Service
public class AdminAuthService {

  private static final String LOGIN_ID_KEY_PREFIX = "ADMIN_LOGIN:id:";
  private static final String IP_KEY_PREFIX = "ADMIN_LOGIN:ip:";

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final FixedWindowRateLimiter rateLimiter;
  private final AdminLoginRateLimitProperties rateLimitProperties;
  // 아이디 없음 경로에서도 실제 해시 비교와 같은 시간이 걸리도록 기동 시 만들어 두는 더미 해시.
  private final String enumerationGuardHash;

  public AdminAuthService(
      final AdminRepository adminRepository,
      final PasswordEncoder passwordEncoder,
      final JwtTokenProvider jwtTokenProvider,
      final FixedWindowRateLimiter rateLimiter,
      final AdminLoginRateLimitProperties rateLimitProperties) {
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.rateLimiter = rateLimiter;
    this.rateLimitProperties = rateLimitProperties;
    this.enumerationGuardHash = passwordEncoder.encode("enumeration-timing-guard");
  }

  /**
   * @param clientIp 호출 제한 키로 쓸 클라이언트 IP ({@code ClientIpResolver} 로 해석한 값)
   */
  @Transactional(readOnly = true)
  public AdminLoginResponse login(
      final String loginId, final String rawPassword, final String clientIp) {
    String loginIdKey = LOGIN_ID_KEY_PREFIX + loginId;
    String ipKey = IP_KEY_PREFIX + clientIp;

    // 두 카운터를 모두 올린 뒤 판정한다 — 단축 평가로 한쪽을 건너뛰면, loginId 한도에 먼저 걸린 공격자가
    // 아이디를 바꿔가며 훑을 때 IP 카운터가 멈춰 스프레이 공격을 놓친다.
    boolean withinLoginIdLimit =
        rateLimiter.tryAcquire(
            loginIdKey,
            rateLimitProperties.maxAttemptsPerLoginId(),
            rateLimitProperties.window());
    boolean withinIpLimit =
        rateLimiter.tryAcquire(
            ipKey, rateLimitProperties.maxAttemptsPerIp(), rateLimitProperties.window());

    if (!withinLoginIdLimit || !withinIpLimit) {
      // 한도 초과는 BCrypt 비교 전에 끊는다 — 요청 폭주가 그대로 CPU 부하가 되지 않도록.
      log.warn("관리자 로그인 호출 제한 초과. loginId={}, clientIp={}", loginId, clientIp);
      throw new BusinessException(ErrorCode.ADMIN_LOGIN_RATE_LIMITED);
    }

    Admin admin = adminRepository.findByLoginId(loginId).orElse(null);

    String storedHash = admin != null ? admin.getPassword() : enumerationGuardHash;
    boolean matched = passwordEncoder.matches(rawPassword, storedHash);

    if (admin == null || !matched) {
      log.warn("관리자 로그인 실패. loginId={}", loginId);
      throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
    }

    // 정상 사용이 확인됐으므로 누적을 되돌린다. 리셋이 없으면 오타를 몇 번 낸 운영자가 성공 후에도
    // 남은 윈도우 동안 재로그인을 못 하게 된다 (관리자 토큰은 refresh 가 없어 재로그인 빈도가 있다).
    rateLimiter.reset(loginIdKey);
    rateLimiter.reset(ipKey);

    return new AdminLoginResponse(jwtTokenProvider.createAdminAccessToken(admin.getId()));
  }
}
