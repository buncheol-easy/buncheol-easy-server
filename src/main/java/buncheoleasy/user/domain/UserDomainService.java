package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
        return userRepository.existsById(userId);
    }

    @Transactional
    public void withdraw(final Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.withdraw();
        userRepository.withdraw(user);
    }

    public User getUser(final Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Transactional
    public void updateProfile(final Long userId, final String nickname, final String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        userRepository.findByNickname(nickname)
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
                    }
                });

        user.updateNickname(nickname);
        user.updatePhoneNumber(phoneNumber);

        userRepository.update(user);
    }

    private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
        User newUser = User.create(
                socialInfo.provider().name(),
                socialInfo.providerId(),
                email
        );
        return userRepository.save(newUser);
    }
}
