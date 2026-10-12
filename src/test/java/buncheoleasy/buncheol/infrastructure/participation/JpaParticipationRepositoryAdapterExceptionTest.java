package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 참여 INSERT 의 제약 위반 변환. 운영 INSERT 는 H2 로 못 태우므로(UTC_TIMESTAMP) JdbcTemplate 을 목으로 두고 MySQL 형식
 * 예외를 던져 catch 분기만 본다. 메시지 판별 자체는 {@code JpaParticipationRepositoryAdapterTest$MemberSlotForeignKeyTest}
 * 가 H2 실메시지로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("참여 INSERT 제약 위반 변환 테스트")
class JpaParticipationRepositoryAdapterExceptionTest {

  private static final String SQL_PREFIX =
      "PreparedStatementCallback; SQL [INSERT INTO participations ...];"
          + " Cannot add or update a child row: a foreign key constraint fails"
          + " (`buncheoleasy`.`participations`, CONSTRAINT ";

  @Mock private JpaParticipationRepository jpaParticipationRepository;
  @Mock private JdbcTemplate jdbcTemplate;

  private JpaParticipationRepositoryAdapter adapterThrowing(final RuntimeException ex) {
    given(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
        .willThrow(ex);
    return new JpaParticipationRepositoryAdapter(jpaParticipationRepository, jdbcTemplate);
  }

  private Participation participation() {
    return Participation.createApplied(7L, 9L, 11L, 13L, 50_000L, 3_000L);
  }

  // 개최자가 뺀 자리로 들어온 신청 — 분철 락 전 스냅샷이 지워진 자리를 보고 INSERT 까지 오면 FK 가 막는다.
  @Test
  void 자리_FK_위반은_500이_아니라_없는_멤버로_거부한다() {
    JpaParticipationRepositoryAdapter adapter =
        adapterThrowing(
            new DataIntegrityViolationException(
                SQL_PREFIX
                    + "`fk_participations_buncheol_member` FOREIGN KEY (`buncheol_member_id`)"
                    + " REFERENCES `buncheol_members` (`id`))"));

    assertThatThrownBy(() -> adapter.saveIfRecruiting(participation()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);
  }

  @Test
  void 추가_모집_INSERT_도_같은_변환을_탄다() {
    JpaParticipationRepositoryAdapter adapter =
        adapterThrowing(
            new DataIntegrityViolationException(
                SQL_PREFIX + "`fk_participations_buncheol_member` FOREIGN KEY ...)"));

    assertThatThrownBy(() -> adapter.saveIfCollecting(participation()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);
  }

  // 넓게 삼키지 않는다 — 다른 FK 위반은 원인이 드러나게 그대로 올라가야 한다.
  @Test
  void 다른_FK_위반은_그대로_올린다() {
    DataIntegrityViolationException otherFk =
        new DataIntegrityViolationException(
            SQL_PREFIX
                + "`fk_participations_user` FOREIGN KEY (`participant_id`) REFERENCES `users` (`id`))");
    JpaParticipationRepositoryAdapter adapter = adapterThrowing(otherFk);

    assertThatThrownBy(() -> adapter.saveIfRecruiting(participation())).isSameAs(otherFk);
  }

  // DuplicateKeyException 은 DataIntegrityViolationException 의 하위 타입이다 — 새 catch 가 기존 유니크 분기를
  // 가로채지 않는지 고정한다.
  @Test
  void 자리_유니크_위반은_기존대로_선착순_마감으로_거부한다() {
    JpaParticipationRepositoryAdapter adapter =
        adapterThrowing(
            new DuplicateKeyException(
                "Duplicate entry '9' for key 'participations.uq_participations_active_member'"));

    assertThatThrownBy(() -> adapter.saveIfRecruiting(participation()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
  }
}
