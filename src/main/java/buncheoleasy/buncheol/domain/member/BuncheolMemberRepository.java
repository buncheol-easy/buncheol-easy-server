package buncheoleasy.buncheol.domain.member;

import java.util.List;
import java.util.Optional;

public interface BuncheolMemberRepository {

  List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers);

  void deleteAllByBuncheolId(Long buncheolId);

  Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(Long buncheolId);

  /** 단일 분철의 멤버 슬롯을 등록 순(id ASC)으로 조회. */
  List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  List<BuncheolMember> findAllByBuncheolIds(List<Long> buncheolIds);

  void deleteById(Long id);
}
