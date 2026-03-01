package buncheoleasy.buncheol.domain.member;

import java.util.List;

public interface BuncheolMemberRepository {

    List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers);

    void deleteAllByBuncheolId(Long buncheolId);
}
