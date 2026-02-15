package buncheoleasy.auth.application;

import buncheoleasy.auth.dto.Tokens;
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
@Transactional(readOnly = true)
public class SocialLoginService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDomainService userDomainService;

    @Transactional
    public Tokens login(final String provider, final String providerId, final String email) {
        SocialInfo socialInfo = SocialInfo.of(provider, providerId);
        User user = userDomainService.getOrCreateBySocialLogin(socialInfo, email);
        return jwtTokenProvider.issueTokens(user.getId());
    }

    public Tokens reissueTokens(final String refreshToken) {
        Long userId = jwtTokenProvider.parseUserIdFromRefreshToken(refreshToken);

        if (!userDomainService.isValidUser(userId)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        return jwtTokenProvider.reissueTokens(userId, refreshToken);
    }
}
