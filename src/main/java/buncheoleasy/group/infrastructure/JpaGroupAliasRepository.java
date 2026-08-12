package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.alias.GroupAlias;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaGroupAliasRepository extends JpaRepository<GroupAlias, Long> {

  // 응답의 별칭 배열 순서가 실행마다 달라지지 않도록 정렬을 명시한다.
  List<GroupAlias> findAllByGroupIdInOrderByGroupIdAscIdAsc(List<Long> groupIds);
}
