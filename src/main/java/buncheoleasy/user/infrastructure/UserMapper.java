package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

  void insert(User user);

  void update(User user);

  Optional<User> findById(Long id);

  Optional<User> findBySocialInfo(
      @Param("provider") String provider, @Param("providerId") String providerId);

  boolean existsById(Long id);

  boolean existsByNicknameExcludingId(
      @Param("nickname") String nickname, @Param("excludeId") Long excludeId);

  void updateDeletedAt(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
