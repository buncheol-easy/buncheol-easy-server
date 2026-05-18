package buncheoleasy.user.infrastructure.favorite;

import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaUserFavoriteGroupRepository extends JpaRepository<UserFavoriteGroup, Long> {

  boolean existsByUserIdAndGroupId(Long userId, Long groupId);

  long deleteByUserIdAndGroupId(Long userId, Long groupId);

  List<UserFavoriteGroup> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
