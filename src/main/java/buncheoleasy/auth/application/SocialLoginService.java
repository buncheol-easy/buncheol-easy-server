package buncheoleasy.auth.application;

import buncheoleasy.auth.dto.Tokens;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
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
        return issueTokens(user.getId());
    }

    private Tokens issueTokens(final Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        return new Tokens(accessToken, refreshToken);
    }
}
