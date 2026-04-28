package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaUserRepository extends JpaRepository<User, Long> {

  @Query(
      "SELECT u FROM User u "
          + "WHERE u.socialInfo.provider = :provider "
          + "AND u.socialInfo.providerId = :providerId")
  Optional<User> findBySocialInfo(
      @Param("provider") SocialProvider provider, @Param("providerId") String providerId);

  @Query("SELECT COUNT(u) > 0 FROM User u " + "WHERE u.nickname = :nickname AND u.id <> :excludeId")
  boolean existsByNicknameAndIdNot(
      @Param("nickname") Nickname nickname, @Param("excludeId") Long excludeId);
}
