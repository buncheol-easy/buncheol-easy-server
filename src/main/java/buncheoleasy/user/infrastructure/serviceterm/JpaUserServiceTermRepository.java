package buncheoleasy.user.infrastructure.serviceterm;

import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaUserServiceTermRepository extends JpaRepository<UserServiceTerm, Long> {

  Optional<UserServiceTerm> findByUserIdAndTag(Long userId, String tag);
}
