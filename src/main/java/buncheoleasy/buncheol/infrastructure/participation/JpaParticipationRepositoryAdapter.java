package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

/**
 * Participation 리포지토리 어댑터. JPA 와 raw JdbcTemplate 을 의도적으로 혼용한다:
 *
 * <ul>
 *   <li>일반 조회·CAS 전이는 {@link JpaParticipationRepository} (Spring Data JPA) 로 처리한다.
 *   <li>원자 조건 INSERT (`INSERT ... SELECT FROM buncheols WHERE status='RECRUITING' ...`) 는 JPA save
 *       로 표현 불가하므로 {@link JdbcTemplate} + {@link KeyHolder} 로 직접 수행하고 generated PK 를 엔티티에 리플렉션으로
 *       주입한다.
 * </ul>
 *
 * <p>상태 전이는 모두 status 를 WHERE 조건으로 둔 {@code @Modifying} CAS 다 — 입금 만료 스케줄러와 호스트 입금확인이 경합해도 정확히 한쪽만
 * 성공한다. 엔티티를 in-memory 로 변경한 뒤 dirty-checking 으로 커밋하지 않으므로 flush 선행으로 CAS WHERE 가 무력화되는 문제가 없다.
 */
@Repository
@RequiredArgsConstructor
public class JpaParticipationRepositoryAdapter implements ParticipationRepository {

  // due_at(Instant, UTC) 을 raw JDBC 로 바인딩할 때 사용. 엔티티 읽기(hibernate.jdbc.time_zone=UTC)와
  // created_at/updated_at(DB UTC_TIMESTAMP())의 UTC 저장 규약을 그대로 맞춘다.
  private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

  private static final Field ID_FIELD;

  static {
    Field field = ReflectionUtils.findField(Participation.class, "id");
    if (field == null) {
      throw new IllegalStateException("Participation.id field not found");
    }
    ReflectionUtils.makeAccessible(field);
    ID_FIELD = field;
  }

  private final JpaParticipationRepository jpaParticipationRepository;
  private final JdbcTemplate jdbcTemplate;

  /** 분철이 모집중이고 마감 전일 때만 삽입하는 conditional INSERT. */
  private static final String INSERT_IF_RECRUITING_SQL =
      "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
          + " shipping_address_id, amount, refund_bank, refund_account, refund_holder,"
          + " due_at, status, created_at, updated_at) "
          + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(), UTC_TIMESTAMP() "
          + "FROM buncheols WHERE id = ? AND status = 'RECRUITING' AND deadline > UTC_TIMESTAMP()";

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
                ps.setLong(5, participation.getAmount());
                ps.setString(6, participation.getRefundAccount().bank());
                ps.setString(7, participation.getRefundAccount().account());
                ps.setString(8, participation.getRefundAccount().holder());
                ps.setTimestamp(9, Timestamp.from(participation.getDueAt()), UTC);
                ps.setString(10, participation.getStatus().name());
                ps.setLong(11, participation.getBuncheolId()); // WHERE id = ?
                return ps;
              },
              keyHolder);
    } catch (DuplicateKeyException ex) {
      // 멤버 슬롯에 이미 활성 참여가 존재(선착순 마감). uq_participations_active_member 위반.
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
  public List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(final Long participantId) {
    return jpaParticipationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);
  }

  @Override
  public boolean existsActiveByParticipantId(final Long participantId) {
    return jpaParticipationRepository.existsByParticipantIdAndStatusIn(
        participantId, ParticipationStatus.active());
  }

  @Override
  public List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(
      final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaParticipationRepository.countActiveByBuncheolIds(
        buncheolIds, ParticipationStatus.active());
  }

  @Override
  public List<Participation> findActiveByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.findByBuncheolIdAndStatusIn(
        buncheolId, ParticipationStatus.active());
  }

  @Override
  public List<Participation> findConfirmedByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.findByBuncheolIdAndStatusOrderByCreatedAtAscIdAsc(
        buncheolId, ParticipationStatus.CONFIRMED);
  }

  @Override
  public int countConfirmedByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.countByBuncheolIdAndStatus(
        buncheolId, ParticipationStatus.CONFIRMED);
  }

  @Override
  public List<Participation> findOverduePaymentTargets(final Instant now, final int limit) {
    return jpaParticipationRepository.findByStatusAndDueAtLessThanEqualOrderByDueAtAsc(
        ParticipationStatus.AWAITING_PAYMENT, now, Limit.of(limit));
  }

  @Override
  public boolean confirmPaymentIfAwaiting(final Long participationId, final Instant now) {
    int updated =
        jpaParticipationRepository.confirmPaymentIfAwaiting(
            participationId,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.CONFIRMED,
            now);
    return updated > 0;
  }

  @Override
  public boolean cancelByParticipantIfAwaiting(final Long participationId, final Instant now) {
    int updated =
        jpaParticipationRepository.cancelIfAwaiting(
            participationId,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.CANCELLED,
            ParticipationCancelReason.SELF_CANCELLED,
            false,
            now);
    return updated > 0;
  }

  @Override
  public boolean expireIfOverdue(final Long participationId, final Instant now) {
    int updated =
        jpaParticipationRepository.cancelIfAwaiting(
            participationId,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.CANCELLED,
            ParticipationCancelReason.PAYMENT_TIMEOUT,
            true,
            now);
    return updated > 0;
  }

  @Override
  public int cancelActiveByBuncheolId(final Long buncheolId, final Instant now) {
    return jpaParticipationRepository.cancelByBuncheolIdAndStatusIn(
        buncheolId,
        ParticipationStatus.active(),
        ParticipationStatus.CANCELLED,
        ParticipationCancelReason.BUNCHEOL_CANCELLED,
        now);
  }
}
