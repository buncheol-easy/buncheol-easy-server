package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

/**
 * Participation 리포지토리 어댑터. JPA 와 raw JdbcTemplate 을 의도적으로 혼용한다:
 *
 * <ul>
 *   <li>일반 CRUD·쿼리는 {@link JpaParticipationRepository} (Spring Data JPA) 로 처리한다.
 *   <li>원자 조건 INSERT (`INSERT ... SELECT FROM buncheols WHERE status='RECRUITING' ...`) 는 JPA save
 *       로 표현 불가하므로 {@link JdbcTemplate} + {@link KeyHolder} 로 직접 수행하고 generated PK 를 회수해 엔티티에
 *       리플렉션으로 주입한다.
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class JpaParticipationRepositoryAdapter implements ParticipationRepository {

  private static final List<ParticipationStatus> ACTIVE_STATUSES =
      List.of(
          ParticipationStatus.ACTIVE_BID,
          ParticipationStatus.AWAITING_PAYMENT,
          ParticipationStatus.CONFIRMED);

  private static final Field ID_FIELD;

  static {
    Field f = ReflectionUtils.findField(Participation.class, "id");
    if (f == null) {
      throw new IllegalStateException("Participation.id field not found");
    }
    ReflectionUtils.makeAccessible(f);
    ID_FIELD = f;
  }

  private final JpaParticipationRepository jpaParticipationRepository;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  /** 분철이 모집중이고 마감 전일 때만 삽입하는 conditional INSERT. */
  private static final String INSERT_IF_RECRUITING_SQL =
      "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
          + " shipping_address_id, bid_amount, status, created_at, updated_at) "
          + "SELECT ?, ?, ?, ?, ?, ?, NOW(), NOW() "
          + "FROM buncheols WHERE id = ? AND status = 'RECRUITING' AND deadline > NOW()";

  @Override
  public boolean saveIfRecruiting(final Participation participation) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    int affected;
    try {
      affected =
          jdbcTemplate.update(
              connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(
                        INSERT_IF_RECRUITING_SQL, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, participation.getBuncheolId());
                ps.setLong(2, participation.getBuncheolMemberId());
                ps.setLong(3, participation.getParticipantId());
                ps.setLong(4, participation.getShippingAddressId());
                ps.setLong(5, participation.getBidAmount());
                ps.setString(6, participation.getStatus().name());
                ps.setLong(7, participation.getBuncheolId()); // WHERE id = ?
                return ps;
              },
              keyHolder);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }
    if (affected == 0) {
      return false;
    }
    Number generatedKey = keyHolder.getKey();
    if (generatedKey != null) {
      ReflectionUtils.setField(ID_FIELD, participation, generatedKey.longValue());
    }
    return true;
  }

  @Override
  public Optional<Participation> findById(final Long id) {
    return jpaParticipationRepository.findById(id);
  }

  @Override
  public Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      final Long buncheolMemberId, final Long participantId) {
    return jpaParticipationRepository.findActiveByBuncheolMemberIdAndParticipantId(
        buncheolMemberId, participantId);
  }

  @Override
  public List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(final Long participantId) {
    return jpaParticipationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);
  }

  @Override
  public boolean existsActiveByParticipantId(final Long participantId) {
    return jpaParticipationRepository.existsActiveByParticipantId(participantId, ACTIVE_STATUSES);
  }

  @Override
  public List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(
      final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaParticipationRepository.countActiveByBuncheolIds(buncheolIds, ACTIVE_STATUSES);
  }

  @Override
  public boolean updateStatus(
      final Participation participation, final ParticipationStatus expectedStatus) {
    int updated =
        jpaParticipationRepository.updateStatusIfMatches(
            participation.getId(),
            participation.getDueAt(),
            participation.getClosedRank(),
            participation.getFailReason(),
            participation.getFinalizedAt(),
            participation.getStatus(),
            Instant.now(clock),
            expectedStatus);
    return updated > 0;
  }
}
