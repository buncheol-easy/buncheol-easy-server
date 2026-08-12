package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 ID/PW 로그인. 아이디 없음과 비밀번호 불일치를 같은 에러({@link ErrorCode#ADMIN_LOGIN_FAILED})로 응답하고, 아이디가 없어도
 * 더미 해시와 BCrypt 비교를 수행해 응답 시간을 맞춘다 — 에러 메시지·타이밍 어느 쪽으로도 계정 존재 여부를 열거할 수 없게 한다. 실패는 감사 로그로 남긴다
 * (저속 크리덴셜 스터핑 탐지용). 성공 시 role claim 이 실린 관리자 access token 을 발급한다 (refresh 없음 — 만료 시 재로그인).
 *
 * <p>여기에 더해 <b>로그인 ID 축 호출 제한</b>을 둔다. 위 방어들은 "어느 계정이 있는지"를 감추지만 추측 자체의 속도는 줄이지 못하는데, 관리자 토큰은
 * 12시간 유효하고 {@code /v1/admin/**} 전체 — 임의 참여의 입금확인, 배송비 환급 승인/반려 — 에 접근하므로 무제한 온라인 추측을 허용할 수 없다.
 * 무차별 대입은 서비스 규모와 무관하게 자동화 스캐너가 무차별로 돌리는 공격이라, 사용자 수가 방어가 되지 않는다.
 *
 * <p><b>IP 축 제한은 두지 않는다.</b> 브라우저 트래픽이 프론트(Next.js)의 {@code /api/backend} 프록시를 거치는데 그 프록시가
 * {@code X-Real-IP}/{@code X-Forwarded-For} 를 전달하지 않아, 모든 관리자 로그인이 프록시 IP 하나로 수렴한다. 그 상태의 IP 한도는
 * 개인별 제한이 아니라 <b>전역 예산</b>이 되어, 익명 공격자가 소량의 요청만으로 모든 운영자의 로그인을 상시 차단할 수 있다.
 *
 * <p>ID 축도 계정 잠금이라 제3자가 운영자 ID 로 도배하면 잠금 자체가 DoS 가 된다. 윈도우를 짧게(기본 10분) 유지해 자동 해제되게 하는 것이 그
 * 완화책이고, 근본 해결은 관리자 계정 다중화다. Redis 장애 시에는 통과시킨다(fail-open) — fail-closed 로 두면 Redis 한 대의 장애가 곧
 * 관리자 로그인 전면 차단(= 입금확인·환급 처리 중단)이 된다.
 */
@Slf4j
@Service
public class AdminAuthService {

  private static final String RATE_LIMIT_KEY_PREFIX = "ADMIN_LOGIN:id:";

  /**
   * INCR 과 만료 설정을 한 번에 처리한다. 둘을 따로 보내면 INCR 직후 만료 설정이 유실됐을 때(커넥션 블립·failover) TTL 없는 키가 남아 해당
   * 계정이 영구히 차단된다.
   */
  private static final RedisScript<Long> INCREMENT_WITH_TTL =
      RedisScript.of(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return count
          """,
          Long.class);

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final StringRedisTemplate redisTemplate;
  private final int maxAttempts;
  private final Duration window;
  // 아이디 없음 경로에서도 실제 해시 비교와 같은 시간이 걸리도록 기동 시 만들어 두는 더미 해시.
  private final String enumerationGuardHash;

  public AdminAuthService(
      final AdminRepository adminRepository,
      final PasswordEncoder passwordEncoder,
      final JwtTokenProvider jwtTokenProvider,
      final StringRedisTemplate redisTemplate,
      @Value("${app.admin.login.rate-limit.max-attempts:5}") final int maxAttempts,
      // 단위를 붙이지 않으면 '분'으로 읽는다 — Spring 기본 단위(밀리초)로 해석되면 제한이 사라진다.
      @DurationUnit(ChronoUnit.MINUTES) @Value("${app.admin.login.rate-limit.window:10m}")
          final Duration window) {
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.redisTemplate = redisTemplate;
    this.maxAttempts = maxAttempts;
    this.window = window;
    this.enumerationGuardHash = passwordEncoder.encode("enumeration-timing-guard");
  }

  @Transactional(readOnly = true)
  public AdminLoginResponse login(final String loginId, final String rawPassword) {
    String rateLimitKey = RATE_LIMIT_KEY_PREFIX + normalizeLoginId(loginId);

    if (!tryAcquire(rateLimitKey)) {
      // 한도 초과는 조회·BCrypt 비교 전에 끊는다 — 요청 폭주가 그대로 CPU 부하가 되지 않도록.
      // loginId 는 로그에 싣지 않는다: 거부 경로는 BCrypt 비용이 없어 rps 가 높고, 어떤 계정이
      // 잠겼는지는 Redis 키로 확인할 수 있다.
      log.warn("관리자 로그인 호출 제한 초과 - 자격증명 검증 없이 거부");
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
    reset(rateLimitKey);

    return new AdminLoginResponse(jwtTokenProvider.createAdminAccessToken(admin.getId()));
  }

  /** 고정 윈도우 카운터를 1 올리고 한도 이내인지 판정한다. Redis 장애는 통과로 처리한다(fail-open). */
  private boolean tryAcquire(final String key) {
    try {
      Long count =
          redisTemplate.execute(
              INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
      return count == null || count <= maxAttempts;
    } catch (DataAccessException exception) {
      log.warn("관리자 로그인 호출 제한 확인 실패 - 제한 없이 통과시킨다", exception);
      return true;
    }
  }

  private void reset(final String key) {
    try {
      redisTemplate.delete(key);
    } catch (DataAccessException exception) {
      // 리셋 실패는 카운터가 윈도우 만료까지 남는 것뿐이라 호출자에게 전파하지 않는다.
      log.warn("관리자 로그인 제한 카운터 초기화 실패", exception);
    }
  }

  /**
   * 제한 키를 계정 조회 기준과 맞춘다. {@code admins} 는 {@code utf8mb4_unicode_ci} 라 {@code Buncheol-Admin} 이
   * 같은 계정을 찾는데, 정규화하지 않으면 Redis 키만 달라져 한도를 우회할 수 있다.
   *
   * <p>이 콜레이션은 대소문자뿐 아니라 <b>악센트도 무시</b>하므로({@code 'a' = 'á'}) {@code toLowerCase} 만으로는 등가가
   * 아니다. 등가를 성립시키는 것은 {@code AdminLoginRequest} 의 ASCII {@code @Pattern} 이다.
   */
  private String normalizeLoginId(final String loginId) {
    return loginId.strip().toLowerCase(Locale.ROOT);
  }
}
