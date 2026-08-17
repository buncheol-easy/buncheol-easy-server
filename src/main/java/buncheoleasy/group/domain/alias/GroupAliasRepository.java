package buncheoleasy.group.domain.alias;

import java.util.List;

/**
 * 별칭은 {@code groups} 와 마찬가지로 SQL 로 직접 시드하므로 애플리케이션 쓰기 경로를 두지 않는다. 검색 매칭 자체는 {@code
 * JpaGroupRepository} 의 그룹 조회 쿼리 안에서 처리되고, 이 포트는 응답에 실을 별칭을 읽어오는 데만 쓴다.
 */
public interface GroupAliasRepository {

  /** 그룹 id 별 별칭 원문. 그룹 목록 응답을 만들 때 그룹당 1회 조회(N+1) 를 피하려고 한 번에 읽는다. */
  List<GroupAlias> findAllByGroupIds(List<Long> groupIds);
}
