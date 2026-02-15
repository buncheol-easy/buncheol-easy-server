package buncheoleasy.user.domain;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findBySocialInfo(SocialInfo socialInfo);

    boolean isValidUserId(Long id);
}
