package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.AppLoginCodeStore;
import buncheoleasy.auth.domain.AppLoginGrant;
import buncheoleasy.auth.domain.AppleIdentity;
import buncheoleasy.auth.domain.AppleIdentityVerifier;
import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.domain.Pkce;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.UserDomainService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 네이티브 앱 로그인. 웹과 같은 가입/로그인 처리({@link SocialLoginService#signIn})를 쓰되, 토큰은 앱 칸({@link
 * LoginClient#APP})으로 발급해 본문으로 돌려준다 — 앱은 웹 쿠키를 쓸 수 없고, 웹 칸을 건드리면 같은 회원의 웹 세션이 끊긴다.
 *
 * <p>카카오는 인증 창 → 서버 콜백 → 일회용 코드(120초, 1회, PKCE challenge 묶음) → 앱이 verifier 와 함께 교환하는 순서다. 앱 주소로 토큰을
 * 바로 보내지 않는 것은 같은 주소 체계를 등록한 다른 앱이 가로챌 수 있어서다(RFC 8252).
 */
@Service
@RequiredArgsConstructor
public class AppLoginService {

  private static final Duration CODE_TIME_TO_LIVE = Duration.ofSeconds(120);
  private static final int CODE_BYTES = 32;
  private static final int NAME_MAX_LENGTH = 30;
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final SocialLoginService socialLoginService;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserDomainService userDomainService;
  private final RefreshTokenStore refreshTokenStore;
  private final AppLoginCodeStore appLoginCodeStore;
  private final AppleIdentityVerifier appleIdentityVerifier;
  private final SecureRandom random = new SecureRandom();

  /** 카카오 앱 로그인 콜백 — 가입/로그인 처리 후 challenge 에 묶인 일회용 코드를 만든다. */
  public AppLoginCode issueLoginCode(final SocialLoginCommand command, final String challenge) {
    if (!Pkce.isValidChallenge(challenge)) {
      throw new BusinessException(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
    }
    LoginAccount account = socialLoginService.signIn(command);
    String code = newCode();
    appLoginCodeStore.save(
        code,
        new AppLoginGrant(account.userId(), challenge, account.newAccount()),
        CODE_TIME_TO_LIVE);
    return new AppLoginCode(code, account.newAccount());
  }

  /**
   * 일회용 코드를 앱 토큰으로 바꾼다. 코드는 verifier 가 틀려도 이미 쓴 것으로 친다 — 코드를 가로챈 쪽이 verifier 를 맞힐 때까지 다시 시도하지
   * 못하게.
   */
  public AppLoginResult exchangeCode(final String code, final String verifier) {
    AppLoginGrant grant =
        appLoginCodeStore
            .consume(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID));
    if (!Pkce.matches(verifier, grant.challenge())) {
      throw new BusinessException(ErrorCode.AUTH_APP_LOGIN_VERIFIER_MISMATCH);
    }
    // 코드 발급 뒤 120초 안에 탈퇴했을 수 있다.
    if (!userDomainService.isValidUser(grant.userId())) {
      throw new BusinessException(ErrorCode.AUTH_APP_LOGIN_CODE_INVALID);
    }
    return new AppLoginResult(
        jwtTokenProvider.issueTokens(grant.userId(), LoginClient.APP), grant.newAccount());
  }

  /**
   * 애플 로그인. 이름은 애플이 첫 로그인 때만 앱에 주므로 앱이 보낸 값을 새 가입에만 쓴다(기존 회원 값은 덮지 않는다 — {@code
   * getOrCreateBySocialLogin}). 전화번호·연령대는 받지 않아 가입 정보 입력 화면으로 가고, C2C 개최 자격은 생기지 않는다.
   */
  public AppLoginResult loginWithApple(
      final String identityToken, final String rawNonce, final String fullName) {
    AppleIdentity identity = appleIdentityVerifier.verify(identityToken, rawNonce);
    LoginAccount account =
        socialLoginService.signIn(
            new SocialLoginCommand(
                SocialProvider.APPLE.name(),
                identity.subject(),
                identity.email(),
                normalizeName(fullName),
                null,
                null,
                false,
                List.of()));
    return new AppLoginResult(
        jwtTokenProvider.issueTokens(account.userId(), LoginClient.APP), account.newAccount());
  }

  /** 앱 refresh 토큰으로 두 토큰을 새로 받는다(회전). 웹 토큰은 받지 않는다. */
  public TokenPair reissueTokens(final String refreshToken) {
    Long userId = jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken, LoginClient.APP);
    if (!userDomainService.isValidUser(userId)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }
    return jwtTokenProvider.reissueTokens(userId, LoginClient.APP, refreshToken);
  }

  /** 앱 로그아웃 — 앱 칸만 지운다. 같은 회원의 웹 세션은 유지된다. */
  public void logout(final Long userId) {
    refreshTokenStore.delete(userId, LoginClient.APP);
  }

  private String newCode() {
    byte[] bytes = new byte[CODE_BYTES];
    random.nextBytes(bytes);
    return BASE64_URL.encodeToString(bytes);
  }

  // 실명 칸(VARCHAR(30))을 넘는 값은 버린다 — 이름 때문에 로그인이 실패하면 안 된다. 가입 정보 화면에서 다시 받는다.
  private static String normalizeName(final String fullName) {
    if (fullName == null) {
      return null;
    }
    String trimmed = fullName.strip();
    if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
      return null;
    }
    return trimmed;
  }
}
