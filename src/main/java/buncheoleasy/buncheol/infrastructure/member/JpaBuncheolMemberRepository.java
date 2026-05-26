package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolMemberRepository extends JpaRepository<BuncheolMember, Long> {

  Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(Long buncheolId);

  List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  @Query("SELECT m FROM BuncheolMember m WHERE m.buncheolId IN :buncheolIds")
  List<BuncheolMember> findAllByBuncheolIds(@Param("buncheolIds") List<Long> buncheolIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM BuncheolMember m WHERE m.buncheolId = :buncheolId")
  void deleteAllByBuncheolId(@Param("buncheolId") Long buncheolId);
}
