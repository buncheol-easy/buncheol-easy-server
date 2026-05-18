package buncheoleasy.user.domain.favorite;

import java.util.List;

public interface UserFavoriteGroupRepository {

  /** 최애 그룹 등록을 저장한다. (user_id, group_id) unique 제약 위반 시 BusinessException 으로 변환된다 (어댑터 책임). */
  UserFavoriteGroup save(UserFavoriteGroup favorite);

  boolean existsByUserIdAndGroupId(Long userId, Long groupId);

  /** 동일 (userId, groupId) 행을 삭제하고 삭제된 row 개수를 반환한다. 0 이면 해제할 최애가 없었다는 의미. */
  int deleteByUserIdAndGroupId(Long userId, Long groupId);

  /** 사용자의 최애 그룹 목록을 최신순(created_at DESC, tie-breaker id DESC) 으로 조회한다. */
  List<UserFavoriteGroup> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  int countByUserId(Long userId);
}
