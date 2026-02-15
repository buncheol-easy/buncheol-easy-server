package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    void insert(User user);

    Optional<User> findById(Long id);

    Optional<User> findBySocialInfo(@Param("provider") String provider, @Param("providerId") String providerId);
}
