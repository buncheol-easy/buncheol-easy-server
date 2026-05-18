package buncheoleasy.user.infrastructure.favorite;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserFavoriteGroupRepositoryAdapter implements UserFavoriteGroupRepository {

  private final JpaUserFavoriteGroupRepository jpaUserFavoriteGroupRepository;

  @Override
  public UserFavoriteGroup save(final UserFavoriteGroup favorite) {
    try {
      return jpaUserFavoriteGroupRepository.save(favorite);
    } catch (DataIntegrityViolationException ex) {
      // unique(user_id, group_id) 제약 위반 — 동시 요청으로 사전 체크 우회 시 후방어
      throw new BusinessException(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);
    }
  }

  @Override
  public boolean existsByUserIdAndGroupId(final Long userId, final Long groupId) {
    return jpaUserFavoriteGroupRepository.existsByUserIdAndGroupId(userId, groupId);
  }

  @Override
  public int deleteByUserIdAndGroupId(final Long userId, final Long groupId) {
    return (int) jpaUserFavoriteGroupRepository.deleteByUserIdAndGroupId(userId, groupId);
  }

  @Override
  public List<UserFavoriteGroup> findAllByUserIdOrderByCreatedAtDescIdDesc(final Long userId) {
    return jpaUserFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
  }

  @Override
  public int countByUserId(final Long userId) {
    return (int) jpaUserFavoriteGroupRepository.countByUserId(userId);
  }
}
