package buncheoleasy.buncheol.infrastructure.member;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolMemberRepository extends JpaRepository<BuncheolMember, Long> {

  Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(Long buncheolId);

  List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  @Query("SELECT m FROM BuncheolMember m WHERE m.buncheolId IN :buncheolIds")
  List<BuncheolMember> findAllByBuncheolIds(@Param("buncheolIds") List<Long> buncheolIds);

  /** 전 슬롯 0원 = 최고가 슬롯이 0원. */
  @Query(
      "SELECT m.buncheolId FROM BuncheolMember m WHERE m.buncheolId IN :buncheolIds "
          + "GROUP BY m.buncheolId HAVING MAX(m.price) = 0")
  List<Long> findAllFreeSlotBuncheolIds(@Param("buncheolIds") List<Long> buncheolIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM BuncheolMember m WHERE m.buncheolId = :buncheolId")
  void deleteAllByBuncheolId(@Param("buncheolId") Long buncheolId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE BuncheolMember m SET m.accessType = :accessType "
          + "WHERE m.id = :id AND m.buncheolId = :buncheolId "
          + "AND NOT EXISTS (SELECT p FROM Participation p "
          + "  WHERE p.buncheolMemberId = m.id AND p.status IN :activeStatuses)")
  int changeAccessTypeIfUnoccupied(
      @Param("id") Long id,
      @Param("buncheolId") Long buncheolId,
      @Param("accessType") BuncheolMemberAccessType accessType,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses);

  boolean existsByBuncheolIdAndMemberId(Long buncheolId, Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM BuncheolMember m WHERE m.buncheolId = :buncheolId")
  List<BuncheolMember> findAllByBuncheolIdForUpdate(@Param("buncheolId") Long buncheolId);

  // 🔴 참조 테이블이 늘면 여기에 NOT EXISTS 를 더해야 한다 — FK 로 자리를 가리키는 것은 지금 participations ·
  // participation_codes 둘이다(둘 다 ON DELETE 없음). 빠뜨려도 FK 가 막고 어댑터가 거부로 바꾸지만 안내 문구가 뭉뚱그려진다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "DELETE FROM BuncheolMember m "
          + "WHERE m.id = :id AND m.buncheolId = :buncheolId "
          + "AND NOT EXISTS (SELECT p FROM Participation p WHERE p.buncheolMemberId = m.id) "
          + "AND NOT EXISTS (SELECT c FROM ParticipationCode c WHERE c.buncheolMemberId = m.id)")
  int deleteIfUnreferenced(@Param("id") Long id, @Param("buncheolId") Long buncheolId);
}
