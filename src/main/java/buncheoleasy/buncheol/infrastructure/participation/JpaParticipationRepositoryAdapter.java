package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.delivery.domain.DeliveryStatus;
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
import java.util.Set;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

  // flow_type 은 buncheols 에서 복사해 비정규화한다 — LEGACY 전용 1인 1참여 유니크
  // (uq_participations_legacy_active_participant)의 generated column 이 참조하기 위함.
  private static final String INSERT_COLUMNS_SQL =
      // refund_* 는 더 이상 싣지 않는다 — 정본이 participation_bundles 로 옮겨갔다 (P2-c).
      // 컬럼은 P4 에서 삭제될 때까지 남고 그 사이 새 행은 NULL 이다(세 칸 모두 NULL 이라 조회도 안전하다).
      "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
          + " shipping_address_id, amount, shipping_fee,"
          + " due_at, status, flow_type, created_at, updated_at) "
          + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, flow_type, UTC_TIMESTAMP(), UTC_TIMESTAMP() ";

  /**
   * 분철이 모집중이고 마감 전일 때만 삽입하는 conditional INSERT.
   *
   * <p>⚠️ "APPLIED 신청은 RECRUITING 분철에만 생긴다"(성사 확정 일괄 전이에서 누락되는 orphan APPLIED 방지)는 정합성은, MySQL
   * REPEATABLE READ 에서 INSERT ... SELECT 의 소스 행(buncheols)에 공유 락이 걸려 성사 확정 UPDATE 와 직렬화되는 동작에
   * 의존한다. READ COMMITTED 로 낮추면 소스 읽기가 비잠금 consistent read 가 되어 이 보장이 깨진다 — 격리수준을 바꾸려면 이 SQL 에
   * FOR SHARE 를 명시할 것 (docs/46 §4.7-C1).
   */
  private static final String INSERT_IF_RECRUITING_SQL =
      INSERT_COLUMNS_SQL
          + "FROM buncheols WHERE id = ? AND status = 'RECRUITING' AND deadline > UTC_TIMESTAMP()";

  /**
   * C2C 추가 모집: 분철이 입금 수집중일 때만 삽입하는 conditional INSERT (docs/46 §4.7-E1). 입금 수집은 deadline 이후
   * 구간이므로 마감 조건 없이 상태만 확인한다 — 진행확정(CONFIRMED) 전이 후에는 삽입되지 않는다.
   */
  private static final String INSERT_IF_COLLECTING_SQL =
      INSERT_COLUMNS_SQL + "FROM buncheols WHERE id = ? AND status = 'PAYMENT_COLLECTING'";

  @Override
  public boolean saveIfRecruiting(final Participation participation) {
    return saveIfBuncheolCondition(participation, INSERT_IF_RECRUITING_SQL);
  }

  @Override
  public boolean saveIfCollecting(final Participation participation) {
    return saveIfBuncheolCondition(participation, INSERT_IF_COLLECTING_SQL);
  }

  private boolean saveIfBuncheolCondition(final Participation participation, final String sql) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    int affected;
    try {
      affected =
          jdbcTemplate.update(
              connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, participation.getBuncheolId());
                ps.setLong(2, participation.getBuncheolMemberId());
                ps.setLong(3, participation.getParticipantId());
                ps.setLong(4, participation.getShippingAddressId());
                ps.setLong(5, participation.getAmount());
                ps.setLong(6, participation.getShippingFee());
                // C2C 신청(APPLIED)은 입금 기한이 없다 — 성사 확정 시 일괄 산정된다.
                ps.setTimestamp(
                    7,
                    participation.getDueAt() == null ? null : Timestamp.from(participation.getDueAt()),
                    UTC);
                ps.setString(8, participation.getStatus().name());
                ps.setLong(9, participation.getBuncheolId()); // WHERE id = ?
                return ps;
              },
              keyHolder);
    } catch (DuplicateKeyException ex) {
      // 어느 유니크 제약에 걸렸는지 인덱스명으로 구분한다 (서비스 사전 체크의 check-then-insert 갭을 DB 가 최종 차단).
      // LEGACY 1인 1참여는 uq_participations_legacy_active_participant 가 담당하고(C2C 는 다슬롯 허용이라
      // 해당 generated column 이 NULL — docs/46 §7.1-11), 구 인덱스명은 ALTER 전 환경 호환용으로 함께 매칭한다.
      if (isViolationOf(ex, "uq_participations_legacy_active_participant")
          || isViolationOf(ex, "uq_participations_active_participant")) {
        // 같은 분철에 참여자의 활성 참여가 이미 존재(분철당 중복 참여 금지).
        throw new BusinessException(ErrorCode.PARTICIPATION_ALREADY_JOINED_BUNCHEOL);
      }
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

  // MySQL·H2 모두 유니크 위반 메시지에 인덱스명이 포함된다 (H2 는 대문자로 노출될 수 있어 대소문자 무시 비교).
  // saveIfRecruiting 은 운영(MySQL) 전용 raw SQL 이라 H2 통합 테스트로 못 태우므로, 메시지 형식 매칭은
  // 단위 테스트에서 직접 검증할 수 있게 package-private 로 열어 둔다.
  static boolean isViolationOf(final DataIntegrityViolationException ex, final String indexName) {
    String message = String.valueOf(ex.getMessage());
    return message.toLowerCase().contains(indexName.toLowerCase());
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
  public boolean existsUnfinishedByParticipantId(final Long participantId) {
    // C2C 신청(APPLIED)·보냈어요(PAYMENT_SENT) 상태도 진행 중 참여로 보고 탈퇴를 막는다 (docs/46 §4.7-D1).
    return jpaParticipationRepository.existsUnfinishedByParticipantId(
        participantId,
        Set.of(
            ParticipationStatus.APPLIED,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.PAYMENT_SENT),
        ParticipationStatus.CONFIRMED,
        PaybackStatus.REQUESTED,
        DeliveryStatus.finished());
  }

  @Override
  public boolean existsActiveByBuncheolIdAndParticipantId(
      final Long buncheolId, final Long participantId) {
    return jpaParticipationRepository.existsByBuncheolIdAndParticipantIdAndStatusIn(
        buncheolId, participantId, ParticipationStatus.active());
  }

  @Override
  public boolean existsActiveByShippingAddressId(final Long shippingAddressId) {
    return jpaParticipationRepository.existsByShippingAddressIdAndStatusIn(
        shippingAddressId, ParticipationStatus.active());
  }

  @Override
  public boolean existsActiveByBuncheolMemberId(final Long buncheolMemberId) {
    return jpaParticipationRepository.existsByBuncheolMemberIdAndStatusIn(
        buncheolMemberId, ParticipationStatus.active());
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
  public List<Long> findActiveBuncheolMemberIds(final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaParticipationRepository.findActiveBuncheolMemberIds(
        buncheolIds, ParticipationStatus.active());
  }

  @Override
  public List<Participation> findActiveByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.findByBuncheolIdAndStatusIn(
        buncheolId, ParticipationStatus.active());
  }

  @Override
  public List<Participation> findCancelledByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.findByBuncheolIdAndStatusOrderByCreatedAtAscIdAsc(
        buncheolId, ParticipationStatus.CANCELLED);
  }

  @Override
  public List<Long> findActiveParticipantIdsByBuncheolIdForUpdate(final Long buncheolId) {
    return jpaParticipationRepository.findParticipantIdsByBuncheolIdAndStatusInForUpdate(
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
  public List<BuncheolConfirmedParticipationCount> countConfirmedByBuncheolIds(
      final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaParticipationRepository.countConfirmedByBuncheolIds(
        buncheolIds, ParticipationStatus.CONFIRMED);
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
  public boolean expirePaymentIfOverdue(final Long participationId, final Instant now) {
    int updated =
        jpaParticipationRepository.cancelIfAwaitingAndOverdue(
            participationId,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.CANCELLED,
            ParticipationCancelReason.PAYMENT_TIMEOUT,
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

  @Override
  public boolean markPaymentSentIfAwaiting(final Long participationId, final Instant now) {
    return jpaParticipationRepository.markPaymentSentIfAwaiting(
            participationId,
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.PAYMENT_SENT,
            now)
        > 0;
  }

  @Override
  public boolean revertPaymentSentIfSent(
      final Long participationId,
      final Instant dueAt,
      final Instant rejectedAt,
      final Instant now) {
    return jpaParticipationRepository.revertPaymentSentIfSent(
            participationId,
            ParticipationStatus.PAYMENT_SENT,
            ParticipationStatus.AWAITING_PAYMENT,
            dueAt,
            rejectedAt,
            now)
        > 0;
  }

  @Override
  public boolean linkBundle(
      final Long participationId, final Long bundleId, final Instant now) {
    return jpaParticipationRepository.linkBundleIfUnlinked(participationId, bundleId, now) > 0;
  }

  @Override
  public boolean cancelByUserIfCancellable(final Long participationId, final Instant now) {
    return jpaParticipationRepository.cancelIfStatusIn(
            participationId,
            // 상태 목록을 여기 다시 적지 않는다 — 판정(ParticipationCancellability)과 CAS 가 각자 들면
            // 한쪽만 바뀌었을 때 응답과 실제 동작이 갈린다 (docs/56 S-1).
            ParticipationCancellability.cancellableStatuses(),
            ParticipationStatus.CANCELLED,
            ParticipationCancelReason.USER_CANCELLED,
            now)
        > 0;
  }

  @Override
  public boolean confirmPaymentIfPayable(final Long participationId, final Instant now) {
    return jpaParticipationRepository.confirmPaymentIfPayable(
            participationId,
            Set.of(ParticipationStatus.AWAITING_PAYMENT, ParticipationStatus.PAYMENT_SENT),
            ParticipationStatus.CONFIRMED,
            now)
        > 0;
  }

  @Override
  public int startPaymentCollecting(final Long buncheolId, final Instant dueAt, final Instant now) {
    return jpaParticipationRepository.startPaymentCollecting(
        buncheolId,
        ParticipationStatus.APPLIED,
        ParticipationStatus.AWAITING_PAYMENT,
        dueAt,
        now);
  }

  @Override
  public List<Participation> findCascadeCancelledByBuncheolId(final Long buncheolId) {
    return jpaParticipationRepository.findByBuncheolIdAndStatusAndCancelReason(
        buncheolId, ParticipationStatus.CANCELLED, ParticipationCancelReason.BUNCHEOL_CANCELLED);
  }

  @Override
  public boolean existsPaybackTweetUrlUsedByOther(
      final String tweetUrl, final Long participationId) {
    return jpaParticipationRepository.existsByPaybackTweetUrlAndIdNot(tweetUrl, participationId);
  }

  @Override
  public void savePaybackRequest(final Participation participation) {
    try {
      // 트랜잭션 커밋까지 미루지 않고 즉시 flush 해 유니크 위반을 이 자리에서 4xx 로 변환한다
      // (커밋 시점에 터지면 어댑터 밖으로 raw DataIntegrityViolationException 이 새어 500 이 된다).
      jpaParticipationRepository.saveAndFlush(participation);
    } catch (DataIntegrityViolationException ex) {
      // 사전 중복 체크의 check-then-update 갭을 DB 유니크가 최종 차단한 경우. 트윗 URL 유니크가 아닌
      // 다른 제약 위반을 중복 신청으로 오분류하지 않도록 인덱스명으로 좁혀 매핑한다.
      if (isViolationOf(ex, "uq_participations_payback_tweet_url")) {
        throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE);
      }
      throw ex;
    }
  }

  @Override
  public boolean completePaybackIfRequested(final Long participationId, final Instant now) {
    int updated =
        jpaParticipationRepository.completePaybackIfRequested(
            participationId, PaybackStatus.REQUESTED, PaybackStatus.COMPLETED, now);
    return updated > 0;
  }

  @Override
  public boolean rejectPaybackIfRequested(
      final Long participationId, final String rejectReason, final Instant now) {
    int updated =
        jpaParticipationRepository.rejectPaybackIfRequested(
            participationId, PaybackStatus.REQUESTED, PaybackStatus.REJECTED, rejectReason, now);
    return updated > 0;
  }
}
