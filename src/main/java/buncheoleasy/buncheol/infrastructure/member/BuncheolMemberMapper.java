package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BuncheolMemberMapper {

    void insertAll(@Param("buncheolMembers") List<BuncheolMember> buncheolMembers);

    void deleteAllByBuncheolId(Long buncheolId);
}
