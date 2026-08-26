package buncheoleasy.buncheol.infrastructure.code;

import buncheoleasy.buncheol.domain.code.ParticipationCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationCodeRepository extends JpaRepository<ParticipationCode, Long> {

  Optional<ParticipationCode> findByCode(String code);

  List<ParticipationCode> findAllByBuncheolIdOrderByIdDesc(Long buncheolId);

  List<ParticipationCode>
      findByBuncheolMemberIdAndUsedAtIsNullAndRevokedAtIsNullOrderByIdDesc(Long buncheolMemberId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationCode c SET c.revokedAt = :now "
          + "WHERE c.buncheolMemberId = :buncheolMemberId "
          + "AND c.usedAt IS NULL AND c.revokedAt IS NULL")
  int revokeOutstandingByBuncheolMemberId(
      @Param("buncheolMemberId") Long buncheolMemberId, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationCode c "
          + "SET c.usedAt = :now, c.usedParticipationId = :participationId "
          + "WHERE c.id = :id AND c.usedAt IS NULL AND c.revokedAt IS NULL AND c.expiresAt > :now")
  int markUsedIfRedeemable(
      @Param("id") Long id,
      @Param("participationId") Long participationId,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ParticipationCode c SET c.revokedAt = :now "
          + "WHERE c.id = :id AND c.usedAt IS NULL AND c.revokedAt IS NULL")
  int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);
}
