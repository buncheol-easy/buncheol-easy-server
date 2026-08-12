package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.admin.dto.response.AdminImpersonationTokenResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.UserDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminImpersonationService 단위 테스트")
class AdminImpersonationServiceTest {

  @InjectMocks private AdminImpersonationService adminImpersonationService;

  @Mock private UserDomainService userDomainService;
  @Mock private JwtTokenProvider jwtTokenProvider;

  @Nested
  @DisplayName("issueToken 테스트")
  class IssueTokenTest {

    @Test
    void 존재하는_유저면_짧은_수명의_유저_토큰을_발급한다() {
      // given
      given(userDomainService.isValidUser(21L)).willReturn(true);
      given(jwtTokenProvider.createImpersonationAccessToken(21L)).willReturn("impersonation-token");
      given(jwtTokenProvider.getImpersonationTokenExpirationSeconds()).willReturn(900L);

      // when
      AdminImpersonationTokenResponse response =
          adminImpersonationService.issueToken(1L, 21L, "결제 상태 미반영 문의 재현");

      // then
      assertThat(response.targetUserId()).isEqualTo(21L);
      assertThat(response.accessToken()).isEqualTo("impersonation-token");
      assertThat(response.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void 존재하지_않는_유저면_USER_NOT_FOUND_로_실패한다() {
      // given
      given(userDomainService.isValidUser(999L)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> adminImpersonationService.issueToken(1L, 999L, "사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);

      then(jwtTokenProvider).shouldHaveNoInteractions();
    }
  }
}
