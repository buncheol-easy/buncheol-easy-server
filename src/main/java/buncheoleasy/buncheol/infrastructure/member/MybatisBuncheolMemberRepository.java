package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisBuncheolMemberRepository implements BuncheolMemberRepository {

  private final BuncheolMemberMapper buncheolMemberMapper;

  @Override
  public List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers) {
    buncheolMemberMapper.insertAll(buncheolMembers);
    return buncheolMembers;
  }

  @Override
  public void deleteAllByBuncheolId(Long buncheolId) {
    buncheolMemberMapper.deleteAllByBuncheolId(buncheolId);
  }

  @Override
  public Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId) {
    return buncheolMemberMapper.findByIdAndBuncheolId(id, buncheolId);
  }
}
