package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleRepository;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ParticipationBundle} 의 <b>DB 매핑</b>과 조회 계약을 검증한다.
 *
 * <p>이 테스트가 필요한 이유: {@code ddl-auto: none} 이라 Hibernate 는 엔티티와 실제 스키마를 대조하지 않는다. 컨텍스트가 뜨는
 * 것만으로는 컬럼이 존재하는지, {@code @Embedded RefundAccount} 가 {@code refund_*} 로 풀리는지, {@code Instant ↔
 * TIMESTAMP} 가 왕복하는지 <b>아무것도 확인되지 않는다</b>. 저장 → flush/clear → 재조회로 실제로 태운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaParticipationBundleRepositoryAdapter 테스트")
class JpaParticipationBundleRepositoryAdapterTest {

  @Autowired private ParticipationBundleRepository participationBundleRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  private Long hostId;
  private Long participantId;
  private Long buncheolId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "bdl_h");
    participantId = TestUserFixture.insertUser(jdbcTemplate, "bdl_p");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "묶음 테스트 그룹");
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(
                groupId,
                "묶음 테스트 분철",
                null,
                "스토어명",
                Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                3,
                3000,
                null,
                FlowType.C2C,
                null),
            Instant.now());
    buncheolRepository.save(buncheol);
    buncheolId = buncheol.getId();
  }

  @Test
  @DisplayName("저장한 묶음을 다시 읽으면 모든 컬럼이 그대로 왕복한다")
  void 저장한_묶음을_다시_읽으면_모든_컬럼이_그대로_왕복한다() {
    ParticipationBundle saved =
        participationBundleRepository.save(
            ParticipationBundle.open(
                buncheolId,
                participantId,
                null,
                3000L,
                RefundAccount.of("국민은행", "12345678", "홍길동")));
    // 1차 캐시에서 그대로 돌려받으면 매핑이 검증되지 않는다 — 실제 SELECT 를 태운다.
    entityManager.flush();
    entityManager.clear();

    ParticipationBundle found = participationBundleRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getBuncheolId()).isEqualTo(buncheolId);
    assertThat(found.getParticipantId()).isEqualTo(participantId);
    assertThat(found.getShippingFee()).isEqualTo(3000L);
    // @Embedded RefundAccount 가 refund_* 세 컬럼으로 풀리는지 — record VO 라 조회 시 생성자를 탄다.
    assertThat(found.getRefundAccount().bank()).isEqualTo("국민은행");
    assertThat(found.getRefundAccount().account()).isEqualTo("12345678");
    assertThat(found.getRefundAccount().holder()).isEqualTo("홍길동");
    // 생성 시점에는 기한·마킹·종료가 모두 비어 있다.
    assertThat(found.getDueAt()).isNull();
    assertThat(found.getPaymentSentAt()).isNull();
    assertThat(found.getClosedAt()).isNull();
    assertThat(found.isActive()).isTrue();
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("한 사람이 같은 분철에 활성 묶음을 2개 가질 수 있다")
  void 한_사람이_같은_분철에_활성_묶음을_2개_가질_수_있다() {
    // 활성 묶음 유니크를 두지 않기로 한 결정(docs/71 §8-3)이 DB 레벨에서 실제로 성립하는지 확인한다.
    // 추가 모집분이 "새 묶음" 이어야 하므로 이 삽입이 막히면 안 된다.
    participationBundleRepository.save(
        ParticipationBundle.open(
            buncheolId, participantId, null, 3000L, RefundAccount.of("국민은행", "12345678", "홍길동")));
    participationBundleRepository.save(
        ParticipationBundle.open(
            buncheolId, participantId, null, 3000L, RefundAccount.of("국민은행", "12345678", "홍길동")));
    entityManager.flush();
    entityManager.clear();

    List<ParticipationBundle> active =
        participationBundleRepository.findActiveByBuncheolIdAndParticipantId(
            buncheolId, participantId);

    assertThat(active).hasSize(2);
  }

  @Test
  @DisplayName("종료된 묶음은 활성 조회에서 빠진다")
  void 종료된_묶음은_활성_조회에서_빠진다() {
    ParticipationBundle closed =
        participationBundleRepository.save(
            ParticipationBundle.open(
                buncheolId,
                participantId,
                null,
                3000L,
                RefundAccount.of("국민은행", "12345678", "홍길동")));
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE participation_bundles SET closed_at = CURRENT_TIMESTAMP WHERE id = ?",
        closed.getId());
    entityManager.clear();

    assertThat(
            participationBundleRepository.findActiveByBuncheolIdAndParticipantId(
                buncheolId, participantId))
        .isEmpty();
    assertThat(participationBundleRepository.findAllByBuncheolId(buncheolId)).hasSize(1);
    assertThat(participationBundleRepository.findById(closed.getId()).orElseThrow().isActive())
        .isFalse();
  }
}
