package buncheoleasy.buncheol.domain.member;

import java.util.List;
import java.util.Optional;

public interface BuncheolMemberRepository {

  List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers);

  void deleteAllByBuncheolId(Long buncheolId);

  Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(Long buncheolId);

  void update(BuncheolMember buncheolMember);

  void deleteById(Long id);
}
