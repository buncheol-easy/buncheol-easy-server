package buncheoleasy.group.domain;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

  boolean existsById(Long id);

  Optional<Group> findById(Long id);

  List<Group> findAllByIds(List<Long> ids);

  /**
   * 정규화된 검색어({@link buncheoleasy.global.query.SearchText})가 그룹명 또는 별칭({@link
   * buncheoleasy.group.domain.alias.GroupAlias})과 부분일치하는 그룹. keyword 가 null 이면 전체.
   */
  List<Group> findByNormalizedKeyword(String normalizedKeyword);

  /** 정규화된 검색어와 그룹명·별칭이 부분일치하는 그룹 id 만. 분철 검색이 그룹명까지 커버하기 위한 사전 해석용. */
  List<Long> findIdsByNormalizedKeyword(String normalizedKeyword);
}
