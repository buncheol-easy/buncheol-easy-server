package buncheoleasy.user.infrastructure.serviceterm;

import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import buncheoleasy.user.domain.serviceterm.UserServiceTermRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserServiceTermRepositoryAdapter implements UserServiceTermRepository {

  private final JpaUserServiceTermRepository jpaUserServiceTermRepository;

  @Override
  public UserServiceTerm save(final UserServiceTerm userServiceTerm) {
    return jpaUserServiceTermRepository.save(userServiceTerm);
  }

  @Override
  public Optional<UserServiceTerm> findByUserIdAndTag(final Long userId, final String tag) {
    return jpaUserServiceTermRepository.findByUserIdAndTag(userId, tag);
  }
}
