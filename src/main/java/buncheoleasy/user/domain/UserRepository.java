package buncheoleasy.user.domain;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    void update(User user);

    Optional<User> findById(Long id);

    Optional<User> findBySocialInfo(SocialInfo socialInfo);

    boolean existsById(Long id);

    Optional<User> findByNickname(String nickname);

    boolean isValidUserId(Long id);

    void withdraw(User user);
}
