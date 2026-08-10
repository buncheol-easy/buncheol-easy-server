package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  public TokenPair login(final SocialLoginCommand command) {
    SocialInfo socialInfo = SocialInfo.of(command.provider(), command.providerId());
    User user =
        userDomainService.getOrCreateBySocialLogin(
            socialInfo, command.email(), command.name(), command.phoneNumber(), command.ageRange());

    if (command.serviceTerms() != null && !command.serviceTerms().isEmpty()) {
      try {
        userDomainService.updateServiceTermAgreements(
            user.getId(), command.serviceTerms(), marketingTermTag);
      } catch (RuntimeException exception) {
        // 약관 내역 저장 실패가 로그인 자체를 막지 않는다 — 재로그인 시 최신 내역으로 재시도된다.
        log.warn("약관 동의 내역 저장 실패: userId={}, reason={}", user.getId(), exception.getMessage());
      }
    }
    return jwtTokenProvider.issueTokens(user.getId());
  }

  public TokenPair reissueTokens(final String refreshToken) {
    Long userId = jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken);

    if (!userDomainService.isValidUser(userId)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }

    return jwtTokenProvider.reissueTokens(userId, refreshToken);
  }

  public void logout(final Long userId) {
    refreshTokenStore.delete(userId);
  }
}
