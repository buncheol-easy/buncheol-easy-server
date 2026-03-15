package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BuncheolMemberMapper {

  void insertAll(@Param("buncheolMembers") List<BuncheolMember> buncheolMembers);

  void deleteAllByBuncheolId(Long buncheolId);

  Optional<BuncheolMember> findByIdAndBuncheolId(
      @Param("id") Long id, @Param("buncheolId") Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(@Param("buncheolId") Long buncheolId);

  void update(BuncheolMember buncheolMember);

  void deleteById(@Param("id") Long id);
}
