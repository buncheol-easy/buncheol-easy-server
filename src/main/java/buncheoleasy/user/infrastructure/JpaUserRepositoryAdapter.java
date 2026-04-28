package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

  private final JpaUserRepository jpaUserRepository;

  @Override
  public User save(User user) {
    return jpaUserRepository.save(user);
  }

  @Override
  public Optional<User> findById(Long id) {
    return jpaUserRepository.findById(id);
  }

  @Override
  public Optional<User> findBySocialInfo(SocialInfo socialInfo) {
    return jpaUserRepository.findBySocialInfo(socialInfo.provider(), socialInfo.providerId());
  }

  @Override
  public boolean existsById(Long id) {
    return jpaUserRepository.existsById(id);
  }

  @Override
  public boolean existsByNicknameExcludingId(String nickname, Long excludeId) {
    return jpaUserRepository.existsByNicknameAndIdNot(Nickname.of(nickname), excludeId);
  }
}
