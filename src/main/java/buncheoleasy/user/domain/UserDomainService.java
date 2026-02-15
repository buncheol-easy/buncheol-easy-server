package buncheoleasy.user.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDomainService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateBySocialLogin(final SocialInfo socialInfo, final String email) {
        return userRepository.findBySocialInfo(socialInfo)
                .orElseGet(() -> createNewSocialUser(socialInfo, email));
    }

    public boolean isValidUser(final Long userId) {
        return userRepository.isValidUserId(userId);
    }

    @Transactional
    public void withdraw(final Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new buncheoleasy.global.exception.domain.BusinessException(
                        buncheoleasy.global.exception.domain.ErrorCode.AUTH_INVALID_TOKEN));
        user.withdraw();
        userRepository.withdraw(user);
    }

    private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
        User newUser = User.create(
                socialInfo.provider().getValue(),
                socialInfo.providerId(),
                email
        );
        return userRepository.save(newUser);
    }
}
