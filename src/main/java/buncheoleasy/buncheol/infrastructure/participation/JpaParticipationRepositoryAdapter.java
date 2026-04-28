package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

@Repository
@RequiredArgsConstructor
public class JpaParticipationRepositoryAdapter implements ParticipationRepository {

  private static final List<ParticipationStatus> ACTIVE_STATUSES =
      List.of(
          ParticipationStatus.PAYMENT_PENDING,
          ParticipationStatus.ACTIVE_BID,
          ParticipationStatus.AWAITING_BALANCE_PAYMENT,
          ParticipationStatus.CONFIRMED);

  private static final List<ParticipationStatus> OPEN_BID_STATUSES =
      List.of(
          ParticipationStatus.PAYMENT_PENDING,
          ParticipationStatus.ACTIVE_BID,
          ParticipationStatus.AWAITING_BALANCE_PAYMENT);

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

  @Override
  public boolean saveInstantIfRecruiting(final Participation participation) {
    return executeConditionalInsert(
        participation,
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, type, instant_price_snapshot, bid_amount, status,"
            + " created_at, updated_at) "
            + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW() "
            + "FROM buncheols WHERE id = ? AND status = 'RECRUITING' AND deadline > NOW()");
  }

  @Override
  public boolean saveBidIfNoActiveInstant(final Participation participation) {
    return executeConditionalInsert(
        participation,
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, type, instant_price_snapshot, bid_amount, status,"
            + " created_at, updated_at) "
            + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW() "
            + "FROM buncheols WHERE id = ? AND status = 'RECRUITING' AND deadline > NOW() "
            + "AND NOT EXISTS (SELECT 1 FROM participations p"
            + " WHERE p.active_instant_member_id = ?)");
  }

  /**
   * 원자적인 conditional INSERT 를 JdbcTemplate + KeyHolder 로 수행한다. JPA bulk INSERT 는 generated key 를
   * 회수하지 못하므로 raw JDBC 를 사용한다. UNIQUE 제약 위반은 DuplicateKeyException 으로 변환되어 BusinessException 으로
   * 전파한다.
   */
  private boolean executeConditionalInsert(final Participation p, final String sql) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    int affected;
    try {
      affected =
          jdbcTemplate.update(
              connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                bindCommonParams(ps, p);
                ps.setLong(9, p.getBuncheolId()); // WHERE id = ?
                if (sql.contains("active_instant_member_id")) {
                  ps.setLong(10, p.getBuncheolMemberId()); // NOT EXISTS subquery
                }
                return ps;
              },
              keyHolder);
    } catch (DuplicateKeyException ex) {
      throw translateDuplicateKey(ex);
    }
    if (affected == 0) {
      return false;
    }
    Number generatedKey = keyHolder.getKey();
    if (generatedKey != null) {
      ReflectionUtils.setField(ID_FIELD, p, generatedKey.longValue());
    }
    return true;
  }

  private void bindCommonParams(final PreparedStatement ps, final Participation p)
      throws java.sql.SQLException {
    ps.setLong(1, p.getBuncheolId());
    ps.setLong(2, p.getBuncheolMemberId());
    ps.setLong(3, p.getParticipantId());
    ps.setLong(4, p.getShippingAddressId());
    ps.setString(5, p.getType().name());
    if (p.getInstantPriceSnapshot() == null) {
      ps.setNull(6, Types.BIGINT);
    } else {
      ps.setLong(6, p.getInstantPriceSnapshot());
    }
    if (p.getBidAmount() == null) {
      ps.setNull(7, Types.BIGINT);
    } else {
      ps.setLong(7, p.getBidAmount());
    }
    ps.setString(8, p.getStatus().name());
  }

  private BusinessException translateDuplicateKey(final DuplicateKeyException ex) {
    final String message = ex.getMessage();
    if (message != null && message.contains("uq_participations_active_member_participant")) {
      return new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }
    return new BusinessException(ErrorCode.PARTICIPATION_MEMBER_ALREADY_TAKEN);
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
  public boolean existsActiveInstantByBuncheolMemberId(final Long buncheolMemberId) {
    return jpaParticipationRepository.existsActiveInstantByBuncheolMemberId(buncheolMemberId);
  }

  @Override
  public boolean existsActiveByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.existsActiveByBuncheolId(buncheolId, ACTIVE_STATUSES);
  }

  @Override
  public List<MemberParticipationPresence> findActiveParticipationPresencesByBuncheolId(
      final Long buncheolId) {
    return jpaParticipationRepository.findActiveParticipationPresenceRows(buncheolId).stream()
        .map(
            row ->
                new MemberParticipationPresence(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).intValue() > 0,
                    ((Number) row[2]).intValue() > 0))
        .toList();
  }

  @Override
  public Set<ShippingMethod> findActiveShippingMethodsByBuncheolId(final Long buncheolId) {
    return new HashSet<>(
        jpaParticipationRepository.findActiveShippingMethodsByBuncheolId(
            buncheolId, ACTIVE_STATUSES));
  }

  @Override
  public boolean updateStatus(
      final Participation participation, final ParticipationStatus expectedStatus) {
    int updated =
        jpaParticipationRepository.updateStatusIfMatches(
            participation.getId(),
            participation.getBalanceDueAmount(),
            participation.getBalanceDueAt(),
            participation.getClosedRank(),
            participation.getFailReason(),
            participation.getFinalizedAt(),
            participation.getStatus(),
            LocalDateTime.now(),
            expectedStatus);
    return updated > 0;
  }

  @Override
  public void failAllOpenBidsByBuncheolMemberId(
      final Long buncheolMemberId, final String failReason) {
    jpaParticipationRepository.failAllOpenBidsByBuncheolMemberId(
        buncheolMemberId, failReason, LocalDateTime.now(), OPEN_BID_STATUSES);
  }
}
