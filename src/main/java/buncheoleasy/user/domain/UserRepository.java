package buncheoleasy.user.domain;

import java.util.Optional;

public interface UserRepository {

  User save(User user);

  void update(User user);

  Optional<User> findById(Long id);

  Optional<User> findBySocialInfo(SocialInfo socialInfo);

  boolean existsById(Long id);

  boolean existsByNicknameExcludingId(String nickname, Long excludeId);

  void withdraw(User user);
}
