package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.AgeRangeRefreshResult;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.SocialLoginUser;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SocialLoginService {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserDomainService userDomainService;
  private final RefreshTokenStore refreshTokenStore;
  private final String marketingTermTag;

  public SocialLoginService(
      final JwtTokenProvider jwtTokenProvider,
      final UserDomainService userDomainService,
      final RefreshTokenStore refreshTokenStore,
      @Value("${app.kakao.marketing-term-tag:marketing}") final String marketingTermTag) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.userDomainService = userDomainService;
    this.refreshTokenStore = refreshTokenStore;
    this.marketingTermTag = marketingTermTag;
  }

  /** 웹 로그인 — 가입/로그인 처리 후 웹 칸 토큰을 발급한다. */
  public SocialLoginResult login(final SocialLoginCommand command) {
    LoginAccount account = signIn(command);
    return new SocialLoginResult(
        jwtTokenProvider.issueTokens(account.userId()), account.newAccount());
  }

  /**
   * 가입/로그인 처리만 하고 토큰은 발급하지 않는다. 앱 로그인은 일회용 코드 교환·애플 로그인에서 앱 칸 토큰을 따로 발급한다 — 여기서 웹 토큰을 발급하면
   * 웹 칸이 덮여 같은 회원의 웹 세션이 끊긴다.
   */
  public LoginAccount signIn(final SocialLoginCommand command) {
    SocialInfo socialInfo = SocialInfo.of(command.provider(), command.providerId());
    SocialLoginUser loginUser = getOrCreate(socialInfo, command);
    User user = loginUser.user();

    if (command.serviceTerms() != null && !command.serviceTerms().isEmpty()) {
      try {
        userDomainService.updateServiceTermAgreements(
            user.getId(), command.serviceTerms(), marketingTermTag);
      } catch (RuntimeException exception) {
        // 약관 내역 저장 실패가 로그인 자체를 막지 않는다 — 재로그인 시 최신 내역으로 재시도된다.
        log.warn("약관 동의 내역 저장 실패: userId={}, reason={}", user.getId(), exception.getMessage());
      }
    }
    return new LoginAccount(user.getId(), loginUser.newAccount());
  }

  /**
   * 같은 소셜 계정의 첫 로그인이 동시에 두 번 오면(버튼 두 번 누름·앱 재시도) 둘 다 "없음"을 보고 가입하다 한쪽이 유니크 인덱스({@code
   * uq_users_active_social_account})에 걸린다. 진 쪽은 트랜잭션이 이미 끝났으므로 새 트랜잭션으로 한 번 더 조회해 먼저 만들어진 회원으로
   * 로그인한다. 닉네임 유니크 충돌도 같은 예외라 재시도가 새 닉네임으로 다시 만든다. 두 번째도 실패하면 그대로 던진다.
   */
  private SocialLoginUser getOrCreate(final SocialInfo socialInfo, final SocialLoginCommand command) {
    try {
      return getOrCreateOnce(socialInfo, command);
    } catch (DataIntegrityViolationException exception) {
      log.info("소셜 가입 동시 충돌 - 다시 조회한다: provider={}", socialInfo.provider());
      return getOrCreateOnce(socialInfo, command);
    }
  }

  private SocialLoginUser getOrCreateOnce(
      final SocialInfo socialInfo, final SocialLoginCommand command) {
    return userDomainService.getOrCreateBySocialLogin(
        socialInfo,
        command.email(),
        command.name(),
        command.phoneNumber(),
        command.ageRange(),
        command.ageRangeWithdrawn());
  }

  /** 연령대 추가 동의 결과를 반영한다. 로그인 상태는 바꾸지 않으므로 토큰을 발급하지 않는다. */
  public AgeRangeRefreshResult refreshAgeRange(
      final String provider,
      final String providerId,
      final String ageRange,
      final boolean ageRangeWithdrawn) {
    return userDomainService.refreshAgeRange(
        SocialInfo.of(provider, providerId), ageRange, ageRangeWithdrawn);
  }

  public TokenPair reissueTokens(final String refreshToken) {
    Long userId = jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken);

    if (!userDomainService.isValidUser(userId)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }

    return jwtTokenProvider.reissueTokens(userId, refreshToken);
  }

  /** 웹 로그아웃 — 웹 칸만 지운다. 같은 회원의 앱 세션은 유지된다. */
  public void logout(final Long userId) {
    refreshTokenStore.delete(userId, LoginClient.WEB);
  }
}
