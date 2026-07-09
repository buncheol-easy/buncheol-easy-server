package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 배포 환경변수({@code ADMIN_LOGIN_ID}/{@code ADMIN_PASSWORD})로 관리자 계정을 부트스트랩한다. BCrypt 해시를 수동으로 만들어
 * INSERT 하는 번거로움을 없애기 위한 시드이며, 같은 loginId 가 이미 있으면 아무것도 하지 않는다(비밀번호 변경 용도가 아님 — 변경은 행 삭제 후 재기동 또는
 * 직접 UPDATE). 환경변수가 없으면 조용히 건너뛴다.
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

  // BCrypt 는 72바이트까지만 반영하며 초과 입력의 encode() 는 예외를 던진다 (Spring Security 7).
  private static final int PASSWORD_MAX_BYTES = 72;
  private static final int PASSWORD_MIN_LENGTH = 12;

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final String bootstrapLoginId;
  private final String bootstrapPassword;

  public AdminAccountInitializer(
      final AdminRepository adminRepository,
      final PasswordEncoder passwordEncoder,
      @Value("${admin.bootstrap.login-id:}") final String bootstrapLoginId,
      @Value("${admin.bootstrap.password:}") final String bootstrapPassword) {
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
    this.bootstrapLoginId = bootstrapLoginId;
    this.bootstrapPassword = bootstrapPassword;
  }

  @Override
  public void run(final ApplicationArguments args) {
    if (!StringUtils.hasText(bootstrapLoginId) || !StringUtils.hasText(bootstrapPassword)) {
      return;
    }
    if (bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
      log.error(
          "부트스트랩 관리자 비밀번호가 BCrypt 한계(72바이트)를 초과해 계정을 생성하지 않습니다. ADMIN_PASSWORD 를 줄여주세요. loginId={}",
          bootstrapLoginId);
      return;
    }
    if (bootstrapPassword.length() < PASSWORD_MIN_LENGTH) {
      // 관리자 로그인은 공개 엔드포인트라 비밀번호 강도가 곧 방어력이다. 생성은 하되 경고를 남긴다.
      log.warn("부트스트랩 관리자 비밀번호가 {}자 미만입니다. 더 긴 비밀번호를 권장합니다.", PASSWORD_MIN_LENGTH);
    }
    if (adminRepository.existsByLoginId(bootstrapLoginId)) {
      return;
    }
    try {
      adminRepository.save(
          Admin.create(bootstrapLoginId, passwordEncoder.encode(bootstrapPassword)));
      log.info("부트스트랩 관리자 계정을 생성했습니다. loginId={}", bootstrapLoginId);
    } catch (final DataIntegrityViolationException exception) {
      // 다중 인스턴스 동시 첫 기동 경합 — unique 제약(uq_admins_login_id)이 진짜 가드이므로 위반은 흡수한다.
      log.info("관리자 계정이 이미 존재해 부트스트랩을 건너뜁니다. loginId={}", bootstrapLoginId);
    }
  }
}
