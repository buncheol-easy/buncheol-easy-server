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

    private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
        User newUser = User.create(
                socialInfo.provider().getValue(),
                socialInfo.providerId(),
                email
        );
        return userRepository.save(newUser);
    }
}
