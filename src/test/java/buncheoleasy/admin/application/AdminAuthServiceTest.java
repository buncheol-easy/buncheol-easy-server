package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthService 단위 테스트")
class AdminAuthServiceTest {

  @InjectMocks private AdminAuthService adminAuthService;

  @Mock private AdminRepository adminRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;

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
      AdminLoginResponse response = adminAuthService.login("buncheol-admin", "raw-password");

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
      assertThatThrownBy(() -> adminAuthService.login("buncheol-admin", "wrong-password"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);

      then(jwtTokenProvider).shouldHaveNoInteractions();
    }
  }
}
