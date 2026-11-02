package buncheoleasy.user.infrastructure.serviceterm;

import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import buncheoleasy.user.domain.serviceterm.UserServiceTermRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserServiceTermRepositoryAdapter implements UserServiceTermRepository {

  private static final String USER_TAG_UNIQUE_INDEX = "uq_user_service_terms_user_tag";

  private final JpaUserServiceTermRepository jpaUserServiceTermRepository;

  @Override
  public UserServiceTerm save(final UserServiceTerm userServiceTerm) {
    return jpaUserServiceTermRepository.save(userServiceTerm);
  }

  @Override
  public void saveIfAbsent(final UserServiceTerm userServiceTerm) {
    try {
      jpaUserServiceTermRepository.save(userServiceTerm);
    } catch (DataIntegrityViolationException ex) {
      // 유니크 위반만 넘긴다 — 다른 무결성 위반까지 삼키면 기록이 안 남았는데 성공으로 응답한다.
      // MySQL·H2 모두 위반 메시지에 인덱스 이름이 실린다(H2 는 대문자).
      if (!String.valueOf(ex.getMessage()).toLowerCase().contains(USER_TAG_UNIQUE_INDEX)) {
        throw ex;
      }
    }
  }

  @Override
  public Optional<UserServiceTerm> findByUserIdAndTag(final Long userId, final String tag) {
    return jpaUserServiceTermRepository.findByUserIdAndTag(userId, tag);
  }
}
