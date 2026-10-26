package buncheoleasy.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.AppLoginCodeStore;
import buncheoleasy.auth.domain.AppLoginGrant;
import buncheoleasy.auth.domain.AppleIdentity;
import buncheoleasy.auth.domain.AppleIdentityVerifier;
import buncheoleasy.auth.domain.AppleLoginAttemptLimiter;
import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppLoginService 단위 테스트")
class AppLoginServiceTest {

  // RFC 7636 Appendix B 예시 값
  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
  private static final Long USER_ID = 7L;
  private static final TokenPair APP_TOKENS = new TokenPair("app-access", "app-refresh");

  @Mock private SocialLoginService socialLoginService;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private UserDomainService userDomainService;
  @Mock private RefreshTokenStore refreshTokenStore;
  @Mock private AppLoginCodeStore appLoginCodeStore;
  @Mock private AppleIdentityVerifier appleIdentityVerifier;
  @Mock private AppleLoginAttemptLimiter appleLoginAttemptLimiter;

  private AppLoginService appLoginService;

  @BeforeEach
  void setUp() {
    appLoginService =
        new AppLoginService(
            socialLoginService,
            jwtTokenProvider,
            userDomainService,
            refreshTokenStore,
            appLoginCodeStore,
            appleIdentityVerifier,
            appleLoginAttemptLimiter);
  }

  private static SocialLoginCommand kakaoCommand() {
    return SocialLoginCommand.of("KAKAO", "kakao-id", "test@example.com");
  }

  @Nested
  @DisplayName("일회용 코드 발급 (카카오 앱 로그인 콜백)")
  class IssueLoginCodeTest {

    @Test
    void 가입_처리_후_challenge_에_묶인_코드를_120초로_저장한다() {
      // given
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, true));

      // when
      AppLoginCode loginCode = appLoginService.issueLoginCode(kakaoCommand(), CHALLENGE);

      // then
      assertThat(loginCode.newAccount()).isTrue();
      assertThat(loginCode.code()).matches("^[A-Za-z0-9_-]{43}$");
      then(appLoginCodeStore)
          .should()
          .save(
              loginCode.code(),
              new AppLoginGrant(USER_ID, CHALLENGE, true),
              Duration.ofSeconds(120));
    }

    @Test
    void 토큰은_발급하지_않는다_웹_칸도_앱_칸도_건드리지_않는다() {
      // given
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, false));

      // when
      appLoginService.issueLoginCode(kakaoCommand(), CHALLENGE);

      // then
      then(jwtTokenProvider).shouldHaveNoInteractions();
      then(refreshTokenStore).shouldHaveNoInteractions();
      then(socialLoginService).should(never()).login(any());
    }

    @Test
    void 코드는_매번_다르다() {
      // given
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, false));

      // when
      String first = appLoginService.issueLoginCode(kakaoCommand(), CHALLENGE).code();
      String second = appLoginService.issueLoginCode(kakaoCommand(), CHALLENGE).code();

      // then
      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void challenge_모양이_다르면_가입_처리도_하지_않는다() {
      // when & then
      assertThatThrownBy(() -> appLoginService.issueLoginCode(kakaoCommand(), "short"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
      then(socialLoginService).shouldHaveNoInteractions();
      then(appLoginCodeStore).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("코드 교환")
  class ExchangeCodeTest {

    @Test
    void verifier_가_맞으면_앱_칸_토큰을_준다() {
      // given
      given(appLoginCodeStore.consume("code"))
          .willReturn(Optional.of(new AppLoginGrant(USER_ID, CHALLENGE, true)));
      given(userDomainService.isValidUser(USER_ID)).willReturn(true);
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);

      // when
      AppLoginResult result = appLoginService.exchangeCode("code", VERIFIER);

      // then
      assertThat(result.token()).isEqualTo(APP_TOKENS);
      assertThat(result.newAccount()).isTrue();
      then(jwtTokenProvider).should(never()).issueTokens(USER_ID);
    }

    @Test
    void verifier_가_다르면_거절하고_코드는_이미_소비됐다() {
      // given
      given(appLoginCodeStore.consume("code"))
          .willReturn(Optional.of(new AppLoginGrant(USER_ID, CHALLENGE, false)));

      // when & then
      assertThatThrownBy(
              () ->
                  appLoginService.exchangeCode(
                      "code", "wrong-verifier-wrong-verifier-wrong-verifier-123"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_VERIFIER_MISMATCH);
      then(appLoginCodeStore).should().consume("code");
      then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    void 이미_쓴_코드나_만료된_코드는_거절한다() {
      // given — 저장소는 소비했거나 TTL 이 지난 코드를 비어 있게 돌려준다
      given(appLoginCodeStore.consume("code")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> appLoginService.exchangeCode("code", VERIFIER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
      then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    void 같은_코드를_두_번_바꾸면_두_번째는_거절한다() {
      // given
      given(appLoginCodeStore.consume("code"))
          .willReturn(Optional.of(new AppLoginGrant(USER_ID, CHALLENGE, false)))
          .willReturn(Optional.empty());
      given(userDomainService.isValidUser(USER_ID)).willReturn(true);
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);
      appLoginService.exchangeCode("code", VERIFIER);

      // when & then
      assertThatThrownBy(() -> appLoginService.exchangeCode("code", VERIFIER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
    }

    @Test
    void 코드_발급_뒤_탈퇴한_회원이면_거절한다() {
      // given
      given(appLoginCodeStore.consume("code"))
          .willReturn(Optional.of(new AppLoginGrant(USER_ID, CHALLENGE, false)));
      given(userDomainService.isValidUser(USER_ID)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> appLoginService.exchangeCode("code", VERIFIER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
      then(jwtTokenProvider).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("애플 로그인")
  class AppleLoginTest {

    private static final String SUBJECT = "001234.0a1b2c3d4e5f60718293a4b5c6d7e8f9.0123";
    private static final String IP = "203.0.113.9";

    @Test
    void 검증한_sub_와_email_로_APPLE_가입_처리하고_앱_칸_토큰을_준다() {
      // given
      given(appleIdentityVerifier.verify("identity-token", "raw-nonce"))
          .willReturn(new AppleIdentity(SUBJECT, "relay@privaterelay.appleid.com"));
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, true));
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);

      // when
      AppLoginResult result =
          appLoginService.loginWithApple("identity-token", "raw-nonce", " 홍길동 ", IP);

      // then
      assertThat(result.token()).isEqualTo(APP_TOKENS);
      assertThat(result.newAccount()).isTrue();
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().signIn(captor.capture());
      SocialLoginCommand command = captor.getValue();
      assertThat(command.provider()).isEqualTo("APPLE");
      assertThat(command.providerId()).isEqualTo(SUBJECT);
      assertThat(command.email()).isEqualTo("relay@privaterelay.appleid.com");
      assertThat(command.name()).isEqualTo("홍길동");
      assertThat(command.phoneNumber()).isNull();
      assertThat(command.ageRange()).isNull();
      assertThat(command.ageRangeWithdrawn()).isFalse();
      assertThat(command.serviceTerms()).isEqualTo(List.of());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          "   ",
          "가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가",
          "홍 길동",
          "John Smith",
          "<!channel>",
          "<https://evil.example|눌러주세요>",
          "*굵게*",
          "홍길동1"
        })
    void 이름이_프로필_실명_규칙에_맞지_않으면_이름_없이_가입한다(final String fullName) {
      // 슬랙 「새로운 참여」 알림에 이름이 그대로 실려 멘션·가짜 링크 문법이 들어가면 안 된다.
      // given
      given(appleIdentityVerifier.verify(anyString(), anyString()))
          .willReturn(new AppleIdentity(SUBJECT, "relay@privaterelay.appleid.com"));
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, false));
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);

      // when
      appLoginService.loginWithApple("t", "n", fullName, IP);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().signIn(captor.capture());
      assertThat(captor.getValue().name()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "Hong", "가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나"})
    void 한글_영문_1_30자_이름은_그대로_쓴다(final String fullName) {
      // given
      given(appleIdentityVerifier.verify(anyString(), anyString()))
          .willReturn(new AppleIdentity(SUBJECT, "relay@privaterelay.appleid.com"));
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, false));
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);

      // when
      appLoginService.loginWithApple("t", "n", fullName, IP);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().signIn(captor.capture());
      assertThat(captor.getValue().name()).isEqualTo(fullName);
    }

    @Test
    void 토큰_검증에_실패하면_그_IP_의_실패를_센다() {
      // given
      given(appleIdentityVerifier.verify("forged", "raw-nonce"))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_TOKEN_INVALID));

      // when & then
      assertThatThrownBy(() -> appLoginService.loginWithApple("forged", "raw-nonce", null, IP))
          .isInstanceOf(BusinessException.class);
      then(appleLoginAttemptLimiter).should().checkAllowed(IP);
      then(appleLoginAttemptLimiter).should().recordFailure(IP);
    }

    @Test
    void 설정_누락이나_애플_장애는_실패로_세지_않는다() {
      // given
      given(appleIdentityVerifier.verify("t", "n"))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_SIGNIN_DISABLED))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_KEYS_UNAVAILABLE));

      // when & then
      assertThatThrownBy(() -> appLoginService.loginWithApple("t", "n", null, IP))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> appLoginService.loginWithApple("t", "n", null, IP))
          .isInstanceOf(BusinessException.class);
      then(appleLoginAttemptLimiter).should(never()).recordFailure(anyString());
    }

    @Test
    void 성공은_세지_않는다() {
      // given
      given(appleIdentityVerifier.verify("t", "n"))
          .willReturn(new AppleIdentity(SUBJECT, "relay@privaterelay.appleid.com"));
      given(socialLoginService.signIn(any())).willReturn(new LoginAccount(USER_ID, false));
      given(jwtTokenProvider.issueTokens(USER_ID, LoginClient.APP)).willReturn(APP_TOKENS);

      // when
      appLoginService.loginWithApple("t", "n", null, IP);

      // then
      then(appleLoginAttemptLimiter).should().checkAllowed(IP);
      then(appleLoginAttemptLimiter).should(never()).recordFailure(anyString());
    }

    @Test
    void 실패가_한도에_닿은_IP_는_토큰을_검증하지_않고_거절한다() {
      // given
      willThrow(new BusinessException(ErrorCode.AUTH_APPLE_LOGIN_RATE_LIMITED))
          .given(appleLoginAttemptLimiter)
          .checkAllowed(IP);

      // when & then
      assertThatThrownBy(() -> appLoginService.loginWithApple("t", "n", null, IP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APPLE_LOGIN_RATE_LIMITED);
      then(appleIdentityVerifier).shouldHaveNoInteractions();
      then(socialLoginService).shouldHaveNoInteractions();
    }

    @Test
    void 애플_토큰_검증이_실패하면_가입_처리도_토큰도_없다() {
      // given
      given(appleIdentityVerifier.verify("forged", "raw-nonce"))
          .willThrow(new BusinessException(ErrorCode.AUTH_APPLE_TOKEN_INVALID));

      // when & then
      assertThatThrownBy(() -> appLoginService.loginWithApple("forged", "raw-nonce", null, IP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_APPLE_TOKEN_INVALID);
      then(socialLoginService).shouldHaveNoInteractions();
      then(jwtTokenProvider).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("앱 토큰 갱신·로그아웃")
  class ReissueAndLogoutTest {

    @Test
    void 앱_refresh_로_앱_칸을_회전한다() {
      // given
      given(jwtTokenProvider.parseUserIdFromRefreshToken("app-refresh", LoginClient.APP))
          .willReturn(USER_ID);
      given(userDomainService.isValidUser(USER_ID)).willReturn(true);
      given(jwtTokenProvider.reissueTokens(USER_ID, LoginClient.APP, "app-refresh"))
          .willReturn(APP_TOKENS);

      // when & then
      assertThat(appLoginService.reissueTokens("app-refresh")).isEqualTo(APP_TOKENS);
    }

    @Test
    void 탈퇴한_회원의_앱_refresh_는_거절한다() {
      // given
      given(jwtTokenProvider.parseUserIdFromRefreshToken("app-refresh", LoginClient.APP))
          .willReturn(USER_ID);
      given(userDomainService.isValidUser(USER_ID)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> appLoginService.reissueTokens("app-refresh"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
      then(jwtTokenProvider).should(never()).reissueTokens(any(), any(), anyString());
    }

    @Test
    void 앱_로그아웃은_앱_칸만_지운다() {
      // when
      appLoginService.logout(USER_ID);

      // then
      then(refreshTokenStore).should().delete(USER_ID, LoginClient.APP);
      then(refreshTokenStore).should(never()).delete(eq(USER_ID), eq(LoginClient.WEB));
      then(refreshTokenStore).should(never()).deleteAll(any());
    }
  }
}
