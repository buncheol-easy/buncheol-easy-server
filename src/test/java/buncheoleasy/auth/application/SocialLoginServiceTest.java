package buncheoleasy.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialLoginService 단위 테스트")
class SocialLoginServiceTest {

  private static final String MARKETING_TAG = "marketing";

  private SocialLoginService socialLoginService;

  @Mock private JwtTokenProvider jwtTokenProvider;

  @Mock private UserDomainService userDomainService;

  @Mock private RefreshTokenStore refreshTokenStore;

  @BeforeEach
  void setUp() {
    socialLoginService =
        new SocialLoginService(
            jwtTokenProvider, userDomainService, refreshTokenStore, MARKETING_TAG);
  }

  @Nested
  @DisplayName("login 테스트")
  class LoginTest {

    @Test
    void 소셜_로그인_시_유저를_조회하고_토큰을_발급한다() {
      // given
      String provider = "KAKAO";
      String providerId = "social_123";
      String email = "test@example.com";
      Long userId = 1L;
      User user = User.create(provider, providerId, email);
      ReflectionTestUtils.setField(user, "id", userId);
      TokenPair expected = new TokenPair("access-token", "refresh-token");

      given(
              userDomainService.getOrCreateBySocialLogin(
                  any(SocialInfo.class), eq(email), isNull(), isNull(), isNull(), eq(false)))
          .willReturn(user);
      given(jwtTokenProvider.issueTokens(userId)).willReturn(expected);

      // when
      TokenPair result = socialLoginService.login(SocialLoginCommand.of(provider, providerId, email));

      // then
      ArgumentCaptor<SocialInfo> captor = ArgumentCaptor.forClass(SocialInfo.class);
      then(userDomainService)
          .should()
          .getOrCreateBySocialLogin(captor.capture(), eq(email), isNull(), isNull(), isNull(), eq(false));
      assertThat(captor.getValue().provider().name()).isEqualTo("KAKAO");
      assertThat(captor.getValue().providerId()).isEqualTo(providerId);

      then(jwtTokenProvider).should().issueTokens(userId);
      assertThat(result).isEqualTo(expected);
      then(userDomainService)
          .should(org.mockito.Mockito.never())
          .updateServiceTermAgreements(anyLong(), anyList(), anyString());
    }

    @Test
    void 카카오싱크_가입은_이름_전화번호_연령대를_전달하고_약관_동의_내역을_저장한다() {
      // given
      Long userId = 2L;
      User user = User.create("KAKAO", "sync_1", "sync@example.com");
      ReflectionTestUtils.setField(user, "id", userId);
      List<ServiceTermAgreement> terms =
          List.of(new ServiceTermAgreement("service_terms", true, Instant.now()));

      given(
              userDomainService.getOrCreateBySocialLogin(
                  any(SocialInfo.class),
                  eq("sync@example.com"),
                  eq("김실명"),
                  eq("01012345678"),
                  eq("20~29"),
                  eq(false)))
          .willReturn(user);
      given(jwtTokenProvider.issueTokens(userId))
          .willReturn(new TokenPair("access", "refresh"));

      // when
      socialLoginService.login(
          new SocialLoginCommand(
              "KAKAO", "sync_1", "sync@example.com", "김실명", "01012345678", "20~29", false, terms));

      // then
      then(userDomainService)
          .should()
          .updateServiceTermAgreements(userId, terms, MARKETING_TAG);
    }

    @Test
    void 약관_동의_내역_저장이_실패해도_로그인은_성공한다() {
      // given
      Long userId = 3L;
      User user = User.create("KAKAO", "sync_2", "sync2@example.com");
      ReflectionTestUtils.setField(user, "id", userId);
      List<ServiceTermAgreement> terms =
          List.of(new ServiceTermAgreement("service_terms", true, Instant.now()));
      TokenPair expected = new TokenPair("access", "refresh");

      given(
              userDomainService.getOrCreateBySocialLogin(
                  any(SocialInfo.class), eq("sync2@example.com"), isNull(), isNull(), isNull(), eq(false)))
          .willReturn(user);
      willThrow(new RuntimeException("DB 오류"))
          .given(userDomainService)
          .updateServiceTermAgreements(userId, terms, MARKETING_TAG);
      given(jwtTokenProvider.issueTokens(userId)).willReturn(expected);

      // when
      TokenPair result =
          socialLoginService.login(
              new SocialLoginCommand("KAKAO", "sync_2", "sync2@example.com", null, null, null, false, terms));

      // then
      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("reissueTokens 테스트")
  class ReissueTokensTest {

    @Test
    void 유효한_유저면_토큰을_재발급한다() {
      // given
      String refreshToken = "valid-refresh-token";
      Long userId = 1L;
      TokenPair expected = new TokenPair("new-access", "new-refresh");

      given(jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken)).willReturn(userId);
      given(userDomainService.isValidUser(userId)).willReturn(true);
      given(jwtTokenProvider.reissueTokens(userId, refreshToken)).willReturn(expected);

      // when
      TokenPair result = socialLoginService.reissueTokens(refreshToken);

      // then
      then(jwtTokenProvider).should().parseUserIdFromRefreshToken(refreshToken);
      then(userDomainService).should().isValidUser(userId);
      then(jwtTokenProvider).should().reissueTokens(userId, refreshToken);
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void 유저가_유효하지_않으면_예외가_발생한다() {
      // given
      String refreshToken = "valid-refresh-token";
      Long userId = 1L;

      given(jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken)).willReturn(userId);
      given(userDomainService.isValidUser(userId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> socialLoginService.reissueTokens(refreshToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

      then(jwtTokenProvider).should().parseUserIdFromRefreshToken(refreshToken);
      then(userDomainService).should().isValidUser(userId);
      then(jwtTokenProvider).shouldHaveNoMoreInteractions();
    }
  }

  @Nested
  @DisplayName("logout 테스트")
  class LogoutTest {

    @Test
    void 로그아웃_시_리프레시_토큰을_삭제한다() {
      // given
      Long userId = 1L;

      // when
      socialLoginService.logout(userId);

      // then
      then(refreshTokenStore).should().delete(userId);
    }
  }
}
