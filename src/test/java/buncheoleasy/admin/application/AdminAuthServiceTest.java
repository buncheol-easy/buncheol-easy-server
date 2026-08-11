package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.admin.infrastructure.AdminLoginRateLimitProperties;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.ratelimit.FixedWindowRateLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthService 단위 테스트")
class AdminAuthServiceTest {

  private static final String LOGIN_ID_KEY = "ADMIN_LOGIN:id:buncheol-admin";

  @Mock private AdminRepository adminRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private FixedWindowRateLimiter rateLimiter;

  private AdminAuthService adminAuthService;

  @BeforeEach
  void setUp() {
    // 한도 안이 기본. 초과 케이스만 개별 테스트에서 덮어쓴다.
    lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    adminAuthService =
        new AdminAuthService(
            adminRepository,
            passwordEncoder,
            jwtTokenProvider,
            rateLimiter,
            new AdminLoginRateLimitProperties(5, Duration.ofMinutes(10)));
  }

  private Admin admin(final Long id) {
    Admin admin = Admin.create("buncheol-admin", "encoded-hash");
    ReflectionTestUtils.setField(admin, "id", id);
    return admin;
  }

  @Nested
  @DisplayName("login 테스트")
  class LoginTest {

    @Test
    void 자격증명이_맞으면_관리자_액세스_토큰을_발급한다() {
      // given
      given(adminRepository.findByLoginId("buncheol-admin")).willReturn(Optional.of(admin(1L)));
      given(passwordEncoder.matches("raw-password", "encoded-hash")).willReturn(true);
      given(jwtTokenProvider.createAdminAccessToken(1L)).willReturn("admin-access-token");

      // when
      AdminLoginResponse response =
          adminAuthService.login("buncheol-admin", "raw-password");

      // then
      assertThat(response.accessToken()).isEqualTo("admin-access-token");
    }

    @Test
    void 존재하지_않는_로그인_ID면_로그인에_실패한다() {
      // given
      given(adminRepository.findByLoginId("unknown")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> adminAuthService.login("unknown", "raw-password"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);

      then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    void 비밀번호가_틀리면_같은_에러로_로그인에_실패한다() {
      // given — 아이디 없음과 같은 에러코드로 응답해 계정 존재 여부를 노출하지 않는다
      given(adminRepository.findByLoginId("buncheol-admin")).willReturn(Optional.of(admin(1L)));
      given(passwordEncoder.matches("wrong-password", "encoded-hash")).willReturn(false);

      // when & then
      assertThatThrownBy(
              () -> adminAuthService.login("buncheol-admin", "wrong-password"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);

      then(jwtTokenProvider).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("호출 제한 테스트")
  class RateLimitTest {

    @Test
    void 한도를_넘으면_조회와_비밀번호_비교_전에_거부한다() {
      // given
      given(rateLimiter.tryAcquire(eq(LOGIN_ID_KEY), anyInt(), any())).willReturn(false);

      // when & then
      assertThatThrownBy(() -> adminAuthService.login("buncheol-admin", "raw-password"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.ADMIN_LOGIN_RATE_LIMITED);

      // 요청 폭주가 그대로 BCrypt CPU 부하가 되지 않도록 조회·비교 전에 끊는다
      then(adminRepository).shouldHaveNoInteractions();
      then(passwordEncoder).should(never()).matches(anyString(), anyString());
      then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    void 대소문자와_앞뒤_공백이_달라도_같은_제한_키를_쓴다() {
      // admins 는 utf8mb4_unicode_ci(대소문자 무시 + PAD SPACE)라 아래 변형이 모두 같은 계정을
      // 찾는다. 정규화하지 않으면 Redis 키만 달라져 한도를 변형 개수만큼 우회할 수 있다.
      // given
      given(rateLimiter.tryAcquire(eq(LOGIN_ID_KEY), anyInt(), any())).willReturn(false);

      // when & then
      for (String variant : List.of("Buncheol-Admin", "BUNCHEOL-ADMIN", "  buncheol-admin  ")) {
        assertThatThrownBy(() -> adminAuthService.login(variant, "raw-password"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ADMIN_LOGIN_RATE_LIMITED);
      }
    }

    @Test
    void 로그인에_성공하면_실패_누적을_초기화한다() {
      // given — 리셋이 없으면 오타를 몇 번 낸 운영자가 성공 후에도 남은 윈도우 동안 재로그인을 못 한다
      given(adminRepository.findByLoginId("buncheol-admin")).willReturn(Optional.of(admin(1L)));
      given(passwordEncoder.matches("raw-password", "encoded-hash")).willReturn(true);
      given(jwtTokenProvider.createAdminAccessToken(1L)).willReturn("admin-access-token");

      // when
      adminAuthService.login("buncheol-admin", "raw-password");

      // then
      then(rateLimiter).should().reset(LOGIN_ID_KEY);
    }

    @Test
    void 로그인에_실패하면_실패_누적을_초기화하지_않는다() {
      // given
      given(adminRepository.findByLoginId("buncheol-admin")).willReturn(Optional.of(admin(1L)));
      given(passwordEncoder.matches("wrong-password", "encoded-hash")).willReturn(false);

      // when
      assertThatThrownBy(() -> adminAuthService.login("buncheol-admin", "wrong-password"))
          .isInstanceOf(BusinessException.class);

      // then
      then(rateLimiter).should(never()).reset(anyString());
    }
  }
}
