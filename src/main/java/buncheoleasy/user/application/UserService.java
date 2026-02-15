package buncheoleasy.user.application;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.user.domain.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDomainService userDomainService;
    private final RefreshTokenStore refreshTokenStore;

    public void withdraw(final Long userId) {
        userDomainService.withdraw(userId);
        refreshTokenStore.delete(userId);
    }
}
