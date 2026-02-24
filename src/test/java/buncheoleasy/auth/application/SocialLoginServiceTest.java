package buncheoleasy.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialLoginService 단위 테스트")
class SocialLoginServiceTest {

    @InjectMocks
    private SocialLoginService socialLoginService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

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
            User user = new User(
                    userId,
                    provider,
                    providerId,
                    "테스트닉네임",
                    email,
                    null,
                    false,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null
            );
            TokenPair expected = new TokenPair("access-token", "refresh-token");

            given(userDomainService.getOrCreateBySocialLogin(any(SocialInfo.class), eq(email))).willReturn(user);
            given(jwtTokenProvider.issueTokens(userId)).willReturn(expected);

            // when
            TokenPair result = socialLoginService.login(provider, providerId, email);

            // then
            ArgumentCaptor<SocialInfo> captor = ArgumentCaptor.forClass(SocialInfo.class);
            then(userDomainService).should().getOrCreateBySocialLogin(captor.capture(), eq(email));
            assertThat(captor.getValue().provider().name()).isEqualTo("KAKAO");
            assertThat(captor.getValue().providerId()).isEqualTo(providerId);

            then(jwtTokenProvider).should().issueTokens(userId);
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
