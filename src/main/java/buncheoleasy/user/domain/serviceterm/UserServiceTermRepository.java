package buncheoleasy.user.domain.serviceterm;

import java.util.Optional;

public interface UserServiceTermRepository {

  UserServiceTerm save(UserServiceTerm userServiceTerm);

  /**
   * 새 행을 넣되 같은 (user_id, tag) 행이 이미 있으면 먼저 들어간 행을 그대로 둔다. 동시 요청의 유니크 위반을 예외 없이 넘기려는 것이라 바깥 트랜잭션 없이
   * 불러야 한다 — 트랜잭션 안에서는 위반이 rollback-only 를 남겨 커밋에서 터진다.
   */
  void saveIfAbsent(UserServiceTerm userServiceTerm);

  Optional<UserServiceTerm> findByUserIdAndTag(Long userId, String tag);
}
