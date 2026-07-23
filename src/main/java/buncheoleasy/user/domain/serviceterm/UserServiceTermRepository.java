package buncheoleasy.user.domain.serviceterm;

import java.util.Optional;

public interface UserServiceTermRepository {

  UserServiceTerm save(UserServiceTerm userServiceTerm);

  Optional<UserServiceTerm> findByUserIdAndTag(Long userId, String tag);
}
