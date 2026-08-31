package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
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
import org.junit.jupiter.api.Nested;
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
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private ParticipationRepository participationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  private Long hostId;
  private Long participantId;
  private Long buncheolId;
  private Long groupId;
  private int memberSeq;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "bdl_h");
    participantId = TestUserFixture.insertUser(jdbcTemplate, "bdl_p");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "묶음 테스트 그룹");
    memberSeq = 0;
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
                RefundAccount.of("국민은행", "12345678", "홍길동"), null));
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
            buncheolId, participantId, null, 3000L, RefundAccount.of("국민은행", "12345678", "홍길동"), null));
    participationBundleRepository.save(
        ParticipationBundle.open(
            buncheolId, participantId, null, 3000L, RefundAccount.of("국민은행", "12345678", "홍길동"), null));
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
                RefundAccount.of("국민은행", "12345678", "홍길동"), null));
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

  // ── 묶음 종료 CAS (P2-b) ────────────────────────────────────────────────────────
  //
  // 이 CAS 가 이 트랙에서 가장 미묘한 자리다. "세어 보고 0이면 닫는다" 로 짜면 같은 묶음의 두 슬롯이 동시에
  // 취소될 때 두 트랜잭션이 서로의 취소를 못 보고 둘 다 안 닫는다. 여기서는 활성 슬롯 존재 판정이 UPDATE 의
  // WHERE 서브쿼리 안에 있는지를 실제 SQL 로 태운다 — 목으로는 증명되지 않는 부분이다.

  private Long openBundle() {
    return openBundle(null);
  }

  private Long openBundle(final Instant dueAt) {
    return participationBundleRepository
        .save(
            ParticipationBundle.open(
                buncheolId,
                participantId,
                null,
                3000L,
                RefundAccount.of("국민", "12345678", "홍길동"),
                dueAt))
        .getId();
  }

  /**
   * 이 묶음에 속한 <b>C2C</b> 참여 한 건을 심는다. LEGACY 는 1인 1활성슬롯 유니크가 걸려 있어 같은 사람의
   * 활성 슬롯을 두 개 만들 수 없다 — 다슬롯 묶음은 C2C 에서만 생기므로 이쪽이 실제와 맞다.
   */
  private void insertC2cParticipation(final Long bundleId, final String status) {
    insertParticipation(bundleId, status);
    jdbcTemplate.update(
        "UPDATE participations SET flow_type = 'C2C' WHERE bundle_id = ? AND flow_type = 'LEGACY'",
        bundleId);
  }

  /** 이 묶음에 속한 참여 한 건을 원하는 상태로 심는다 (멤버 슬롯 FK 를 함께 만든다). */
  private void insertParticipation(final Long bundleId, final String status) {
    // 멤버 슬롯은 (분철, 그룹멤버) 유니크라 참여마다 새 그룹 멤버가 필요하다.
    Long freshGroupMemberId =
        TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "묶음 멤버 " + (memberSeq++));
    BuncheolMember member = BuncheolMember.create(buncheolId, freshGroupMemberId, 10_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    entityManager.flush();
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, bundle_id,"
            + " shipping_address_id, amount, shipping_fee, refund_bank, refund_account,"
            + " refund_holder, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, NULL, 10000, 3000, '국민', '12345678', '홍길동', ?,"
            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        buncheolId,
        member.getId(),
        participantId,
        bundleId,
        status);
  }

  @Test
  @DisplayName("활성 슬롯이 남아 있으면 묶음을 닫지 않는다")
  void 활성_슬롯이_남아_있으면_묶음을_닫지_않는다() {
    Long bundleId = openBundle();
    insertParticipation(bundleId, "CANCELLED");
    insertParticipation(bundleId, "CONFIRMED");
    entityManager.flush();
    entityManager.clear();

    boolean closed = participationBundleRepository.closeIfNoActiveSlots(bundleId, Instant.now());

    assertThat(closed).isFalse();
    assertThat(participationBundleRepository.findById(bundleId))
        .get()
        .extracting(ParticipationBundle::isActive)
        .isEqualTo(true);
  }

  @Test
  @DisplayName("활성 슬롯이 하나도 없으면 묶음을 닫는다")
  void 활성_슬롯이_하나도_없으면_묶음을_닫는다() {
    Long bundleId = openBundle();
    insertParticipation(bundleId, "CANCELLED");
    entityManager.flush();
    entityManager.clear();

    boolean closed = participationBundleRepository.closeIfNoActiveSlots(bundleId, Instant.now());

    assertThat(closed).isTrue();
    assertThat(participationBundleRepository.findById(bundleId))
        .get()
        .extracting(ParticipationBundle::isActive)
        .isEqualTo(false);
  }

  // 두 슬롯이 각각 취소되며 두 번 호출되는 상황. 두 번째는 이미 닫혀 있어 false 여야 한다 — 그래야 호출부가
  // 취소 경로마다 무조건 호출해도 안전하다(멱등).
  @Test
  @DisplayName("이미 닫힌 묶음을 다시 닫으려 하면 false 다")
  void 이미_닫힌_묶음을_다시_닫으려_하면_false다() {
    Long bundleId = openBundle();
    insertParticipation(bundleId, "CANCELLED");
    entityManager.flush();
    entityManager.clear();
    participationBundleRepository.closeIfNoActiveSlots(bundleId, Instant.now());
    entityManager.clear();

    assertThat(participationBundleRepository.closeIfNoActiveSlots(bundleId, Instant.now()))
        .isFalse();
  }

  @Test
  @DisplayName("분철 일괄 닫기는 빈 묶음만 닫고 살아 있는 묶음은 건드리지 않는다")
  void 분철_일괄_닫기는_빈_묶음만_닫는다() {
    Long empty = openBundle();
    Long alive = openBundle();
    insertParticipation(empty, "CANCELLED");
    insertParticipation(alive, "CONFIRMED");
    entityManager.flush();
    entityManager.clear();

    int closed = participationBundleRepository.closeEmptyByBuncheolId(buncheolId, Instant.now());

    assertThat(closed).isEqualTo(1);
    entityManager.clear();
    assertThat(participationBundleRepository.findById(empty)).get().extracting(
            ParticipationBundle::isActive).isEqualTo(false);
    assertThat(participationBundleRepository.findById(alive)).get().extracting(
            ParticipationBundle::isActive).isEqualTo(true);
  }

  // 슬롯이 하나도 없는 묶음(고아)은 닫지 않는다 — 참여 INSERT 와 연결 사이에서 롤백되면 애초에 남지 않지만,
  // 조건에 EXISTS 가 빠지면 "만들자마자 닫히는" 묶음이 생겨 첫 참여가 곧장 시체가 된다.
  @Test
  @DisplayName("슬롯이 아직 없는 묶음은 일괄 닫기 대상이 아니다")
  void 슬롯이_아직_없는_묶음은_일괄_닫기_대상이_아니다() {
    Long bundleId = openBundle();
    entityManager.flush();
    entityManager.clear();

    assertThat(participationBundleRepository.closeEmptyByBuncheolId(buncheolId, Instant.now()))
        .isZero();
  }

  @Test
  @DisplayName("성사 확정 기한 채우기는 기한이 빈 활성 묶음만 채운다")
  void 성사_확정_기한_채우기는_기한이_빈_활성_묶음만_채운다() {
    Long bundleId = openBundle();
    entityManager.flush();
    entityManager.clear();
    Instant dueAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    int filled = participationBundleRepository.assignDueAtByBuncheolId(
        buncheolId, dueAt, Instant.now());

    assertThat(filled).isEqualTo(1);
    entityManager.clear();
    assertThat(participationBundleRepository.findById(bundleId))
        .get()
        .extracting(ParticipationBundle::getDueAt)
        .isEqualTo(dueAt);

    // 이미 채워진 묶음은 다시 덮어쓰지 않는다 — 반려로 연장된 기한이 되돌아가면 안 된다.
    entityManager.clear();
    assertThat(participationBundleRepository.assignDueAtByBuncheolId(
            buncheolId, dueAt.plus(1, ChronoUnit.DAYS), Instant.now()))
        .isZero();
  }

  // 🔴 닫힌 묶음에는 붙지 않는다 — 재사용 후보는 비잠금 조회로 뽑히므로, 그 사이 마지막 슬롯이 취소돼
  // 묶음이 닫혔을 수 있다. 그대로 붙이면 「닫혔는데 활성 슬롯을 가진 묶음」이 생기고, 종료 CAS 가
  // closed_at IS NULL 을 요구하므로 그 묶음은 두 번 다시 닫히지 않는다.
  @Test
  @DisplayName("이미 닫힌 묶음에는 참여를 연결하지 않는다")
  void 이미_닫힌_묶음에는_참여를_연결하지_않는다() {
    Long bundleId = openBundle();
    insertParticipation(bundleId, "CANCELLED");
    entityManager.flush();
    entityManager.clear();
    participationBundleRepository.closeIfNoActiveSlots(bundleId, Instant.now());
    entityManager.clear();

    Long orphanId = insertUnlinkedParticipation();

    assertThat(participationRepository.linkBundle(orphanId, bundleId, Instant.now())).isFalse();
  }

  @Test
  @DisplayName("열려 있는 묶음에는 참여를 연결한다")
  void 열려_있는_묶음에는_참여를_연결한다() {
    Long bundleId = openBundle();
    entityManager.flush();
    entityManager.clear();

    Long orphanId = insertUnlinkedParticipation();

    assertThat(participationRepository.linkBundle(orphanId, bundleId, Instant.now())).isTrue();
  }

  // 덮어쓰면 그 사람의 이체가 엉뚱한 묶음으로 옮겨간다 — 재실행·경합에서 지켜야 하는 절반이다.
  @Test
  @DisplayName("이미 묶음에 붙은 참여는 다른 묶음으로 덮어쓰지 않는다")
  void 이미_묶음에_붙은_참여는_덮어쓰지_않는다() {
    Long firstBundleId = openBundle();
    Long otherBundleId = openBundle();
    entityManager.flush();
    entityManager.clear();
    Long participationId = insertUnlinkedParticipation();
    assertThat(participationRepository.linkBundle(participationId, firstBundleId, Instant.now()))
        .isTrue();
    entityManager.clear();

    assertThat(participationRepository.linkBundle(participationId, otherBundleId, Instant.now()))
        .isFalse();
  }

  /** 아직 묶음에 붙지 않은 참여 한 건. */
  private Long insertUnlinkedParticipation() {
    Long freshGroupMemberId =
        TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "미연결 멤버 " + (memberSeq++));
    BuncheolMember member = BuncheolMember.create(buncheolId, freshGroupMemberId, 10_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    entityManager.flush();
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, bundle_id,"
            + " shipping_address_id, amount, shipping_fee, refund_bank, refund_account,"
            + " refund_holder, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, 10000, 3000, '국민', '12345678', '홍길동',"
            + " 'AWAITING_PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        buncheolId,
        member.getId(),
        participantId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE buncheol_member_id = ?", Long.class, member.getId());
  }
  @Nested
  @DisplayName("markBundlePaymentSent — 묶음 단위 「보냈어요」")
  class MarkBundlePaymentSentTest {

    @Test
    @DisplayName("묶음의 입금 대기 슬롯을 한 번에 마킹한다")
    void 입금_대기_슬롯을_한_번에_마킹한다() {
      Long bundleId = openBundle(Instant.now().plusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      entityManager.clear();

      int marked = participationRepository.markBundlePaymentSent(bundleId, Instant.now());

      assertThat(marked).isEqualTo(2);
      assertThat(statusCount(bundleId, "PAYMENT_SENT")).isEqualTo(2);
    }

    // 🔴 기한이 지난 뒤에도 열려 있어야 한다 — 늦게 보낸 사람도 보냈다는 사실을 남길 수 있어야 개최자가 확인한다.
    @Test
    @DisplayName("입금 기한이 지나도 마킹할 수 있다")
    void 기한이_지나도_마킹할_수_있다() {
      Long bundleId = openBundle(Instant.now().minusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      entityManager.clear();

      assertThat(participationRepository.markBundlePaymentSent(bundleId, Instant.now()))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("이미 마킹됐거나 확정된 슬롯은 건드리지 않는다")
    void 이미_마킹됐거나_확정된_슬롯은_건드리지_않는다() {
      Long bundleId = openBundle(Instant.now().plusSeconds(3600));
      insertC2cParticipation(bundleId, "PAYMENT_SENT");
      insertC2cParticipation(bundleId, "CONFIRMED");
      entityManager.flush();
      entityManager.clear();

      assertThat(participationRepository.markBundlePaymentSent(bundleId, Instant.now())).isZero();
      assertThat(statusCount(bundleId, "CONFIRMED")).isEqualTo(1);
    }

    // 🔴 이 왕복 검증이 없어서 "나노초 Instant 를 초 단위 DATETIME 과 equals 비교" 하는 버그가
    // 테스트를 전부 통과하고 staging 에서야 드러났다. H2 는 정밀도를 보존하므로 여기서는 초 단위로 잘라
    // 비교해, MySQL 이 잘라 저장해도 성립하는 계약만 단언한다.
    @Test
    @DisplayName("CAS 가 쓴 payment_sent_at 이 넘긴 시각과 초 단위로 일치한다")
    void CAS가_쓴_시각이_넘긴_시각과_초_단위로_일치한다() {
      Long bundleId = openBundle(Instant.now().plusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      entityManager.clear();
      Instant now = Instant.parse("2026-08-31T12:00:00.123456789Z");

      participationRepository.markBundlePaymentSent(bundleId, now);
      entityManager.clear();

      Instant stored =
          participationRepository.findAllByBundleIds(List.of(bundleId)).stream()
              .findFirst()
              .orElseThrow()
              .getPaymentSentAt();
      assertThat(stored.truncatedTo(ChronoUnit.SECONDS))
          .isEqualTo(now.truncatedTo(ChronoUnit.SECONDS));
    }

    // 이미 마킹된 슬롯의 시각을 덮어쓰면 "언제 보냈다고 했는가" 가 사라진다 — 분쟁 증거다.
    @Test
    @DisplayName("이미 마킹된 슬롯의 시각은 덮어쓰지 않는다")
    void 이미_마킹된_슬롯의_시각은_덮어쓰지_않는다() {
      Long bundleId = openBundle(Instant.now().plusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      Instant first = Instant.parse("2026-08-31T10:00:00Z");
      participationRepository.markBundlePaymentSent(bundleId, first);
      entityManager.clear();

      participationRepository.markBundlePaymentSent(bundleId, first.plusSeconds(3600));
      entityManager.clear();

      Instant stored =
          participationRepository.findAllByBundleIds(List.of(bundleId)).stream()
              .findFirst()
              .orElseThrow()
              .getPaymentSentAt();
      assertThat(stored.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(first);
    }

    private int statusCount(final Long bundleId, final String status) {
      return jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM participations WHERE bundle_id = ? AND status = ?",
          Integer.class,
          bundleId,
          status);
    }
  }

  @Nested
  @DisplayName("extendDueAt — 개최자 반려 시 묶음 기한 연장")
  class ExtendDueAtTest {

    // 🔴 이 가드가 이 기능의 안전성 근거다 — 기한이 앞으로 당겨지면 「제외」가 열려, 반려로 24h 를 더 받은
    // 정상 입금 대기자를 개최자가 바로 뺄 수 있게 된다.
    @Test
    @DisplayName("이미 더 뒤인 기한은 앞으로 당겨지지 않는다")
    void 이미_더_뒤인_기한은_당겨지지_않는다() {
      Instant later = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.SECONDS);
      Long bundleId = openBundle(later);
      entityManager.flush();
      entityManager.clear();

      participationBundleRepository.extendDueAt(
          bundleId, later.minusSeconds(3600), Instant.now());
      entityManager.clear();

      assertThat(participationBundleRepository.findById(bundleId))
          .get()
          .extracting(ParticipationBundle::getDueAt)
          .isEqualTo(later);
    }

    @Test
    @DisplayName("더 뒤로 미는 것은 반영된다")
    void 더_뒤로_미는_것은_반영된다() {
      Instant original = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
      Instant extended = original.plusSeconds(86_400);
      Long bundleId = openBundle(original);
      entityManager.flush();
      entityManager.clear();

      participationBundleRepository.extendDueAt(bundleId, extended, Instant.now());
      entityManager.clear();

      assertThat(participationBundleRepository.findById(bundleId))
          .get()
          .extracting(ParticipationBundle::getDueAt)
          .isEqualTo(extended);
    }

    @Test
    @DisplayName("이미 닫힌 묶음의 기한은 밀지 않는다")
    void 이미_닫힌_묶음의_기한은_밀지_않는다() {
      Instant original = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
      Long bundleId = openBundle(original);
      entityManager.flush();
      jdbcTemplate.update(
          "UPDATE participation_bundles SET closed_at = CURRENT_TIMESTAMP WHERE id = ?", bundleId);
      entityManager.clear();

      participationBundleRepository.extendDueAt(
          bundleId, original.plusSeconds(86_400), Instant.now());
      entityManager.clear();

      assertThat(participationBundleRepository.findById(bundleId))
          .get()
          .extracting(ParticipationBundle::getDueAt)
          .isEqualTo(original);
    }
  }

  /**
   * 「제외」 CAS 는 {@code ParticipationRepository} 에 있지만 묶음 픽스처가 여기 있어 함께 둔다. 검증 대상이
   * <b>가드가 UPDATE WHERE 안에서 원자적으로 도는가</b> 라 실제 DB 로 돌려야 의미가 있다.
   */
  @Nested
  @DisplayName("releaseBundleIfDue — 개최자 「제외」")
  class ReleaseBundleIfDueTest {

    @Test
    @DisplayName("기한이 지났으면 활성 슬롯을 전부 취소한다")
    void 기한이_지났으면_활성_슬롯을_전부_취소한다() {
      Long bundleId = openBundle(Instant.now().minusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      insertC2cParticipation(bundleId, "PAYMENT_SENT");
      entityManager.flush();
      entityManager.clear();

      int released = participationRepository.releaseBundleIfDue(bundleId, Instant.now());

      assertThat(released).isEqualTo(2);
      assertThat(activeSlotCount(bundleId)).isZero();
      assertThat(cancelReasons(bundleId)).containsOnly("HOST_RELEASED");
    }

    // 🔴 모집 중에는 기한이 없다 — 가드가 fail-closed 라 여기서 막힌다.
    @Test
    @DisplayName("기한이 없으면(모집 중) 한 건도 취소하지 않는다")
    void 기한이_없으면_한_건도_취소하지_않는다() {
      Long bundleId = openBundle(null);
      insertC2cParticipation(bundleId, "APPLIED");
      entityManager.flush();
      entityManager.clear();

      int released = participationRepository.releaseBundleIfDue(bundleId, Instant.now());

      assertThat(released).isZero();
      assertThat(activeSlotCount(bundleId)).isEqualTo(1);
    }

    @Test
    @DisplayName("기한 전이면 한 건도 취소하지 않는다")
    void 기한_전이면_한_건도_취소하지_않는다() {
      Long bundleId = openBundle(Instant.now().plusSeconds(3600));
      insertC2cParticipation(bundleId, "PAYMENT_SENT");
      entityManager.flush();
      entityManager.clear();

      int released = participationRepository.releaseBundleIfDue(bundleId, Instant.now());

      assertThat(released).isZero();
      assertThat(activeSlotCount(bundleId)).isEqualTo(1);
    }

    /**
     * ⚠️ <b>확정 슬롯 검사는 이 CAS 에 없다.</b> MySQL 이 {@code UPDATE} 대상 테이블을 서브쿼리 FROM 에서
     * 참조하는 것을 금지하기 때문이다(error 1093). 그 검사는 서비스가 {@link
     * buncheoleasy.buncheol.domain.participation.ParticipationRepository#findAllByBundleIdForUpdate}
     * 로 슬롯을 잠근 뒤 수행한다 — {@code ParticipationBundleServiceTest} 가 그쪽을 검증한다.
     *
     * <p>🔴 <b>H2 는 {@code MODE=MySQL} 이어도 이 제약을 재현하지 않는다.</b> 원래 조건이 들어간 CAS 가 여기서
     * 통과하고 staging(MySQL)에서야 500 으로 드러났다. 같은 테이블을 보는 조건은 CAS 에 넣지 말 것.
     */
    @Test
    @DisplayName("확정 슬롯 유무는 이 CAS 가 보지 않는다 — 잠금 조회 뒤 서비스가 판정한다")
    void 확정_슬롯_유무는_이_CAS_가_보지_않는다() {
      Long bundleId = openBundle(Instant.now().minusSeconds(3600));
      insertC2cParticipation(bundleId, "CONFIRMED");
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      entityManager.clear();

      int released = participationRepository.releaseBundleIfDue(bundleId, Instant.now());

      // 미입금 슬롯만 빠지고 확정분은 남는다 — 실제 차단은 서비스의 판정이 한다.
      assertThat(released).isEqualTo(1);
      assertThat(statusCountForRelease(bundleId, "CONFIRMED")).isEqualTo(1);
    }

    private int statusCountForRelease(final Long bundleId, final String status) {
      return jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM participations WHERE bundle_id = ? AND status = ?",
          Integer.class,
          bundleId,
          status);
    }

    @Test
    @DisplayName("이미 닫힌 묶음은 제외 대상이 아니다")
    void 이미_닫힌_묶음은_제외_대상이_아니다() {
      Long bundleId = openBundle(Instant.now().minusSeconds(3600));
      insertC2cParticipation(bundleId, "AWAITING_PAYMENT");
      entityManager.flush();
      jdbcTemplate.update(
          "UPDATE participation_bundles SET closed_at = CURRENT_TIMESTAMP WHERE id = ?", bundleId);
      entityManager.clear();

      int released = participationRepository.releaseBundleIfDue(bundleId, Instant.now());

      assertThat(released).isZero();
    }

    private int activeSlotCount(final Long bundleId) {
      return jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM participations WHERE bundle_id = ?"
              + " AND status IN ('APPLIED','AWAITING_PAYMENT','PAYMENT_SENT','CONFIRMED')",
          Integer.class,
          bundleId);
    }

    private List<String> cancelReasons(final Long bundleId) {
      return jdbcTemplate.queryForList(
          "SELECT cancel_reason FROM participations WHERE bundle_id = ? AND status = 'CANCELLED'",
          String.class,
          bundleId);
    }
  }

}
