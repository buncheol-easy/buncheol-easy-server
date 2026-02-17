package buncheoleasy.auth.application;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDomainService userDomainService;
    private final RefreshTokenStore refreshTokenStore;

    public TokenPair login(final String provider, final String providerId, final String email) {
        SocialInfo socialInfo = SocialInfo.of(provider, providerId);
        User user = userDomainService.getOrCreateBySocialLogin(socialInfo, email);
        return jwtTokenProvider.issueTokens(user.getId());
    }

    @Transactional(readOnly = true)
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
