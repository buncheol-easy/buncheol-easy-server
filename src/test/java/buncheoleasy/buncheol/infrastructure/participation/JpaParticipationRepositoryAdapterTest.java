package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.domain.participation.PaybackTweetUrl;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaParticipationRepositoryAdapter 테스트")
class JpaParticipationRepositoryAdapterTest {

  private final ShippingFeeAttribution fees = ShippingFeeAttribution.empty();

  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");

  @Autowired private ParticipationRepository participationRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long participantId;
  // 분철당 활성 참여는 참여자별 1건(uq_participations_active_participant)이라, 같은 분철에
  // 활성 참여를 두 건 이상 깔아야 하는 픽스처는 두 번째 유저를 쓴다.
  private Long secondParticipantId;
  private Long groupId;
  private Long groupMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_xx");
    participantId = TestUserFixture.insertUser(jdbcTemplate, "participant_xx");
    secondParticipantId = TestUserFixture.insertUser(jdbcTemplate, "participant2_xx");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹");
    groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "테스트 멤버");
  }

  private Long createBuncheol() {
    return createBuncheol(1);
  }

  private Long createBuncheol(final int minHeadcount) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, minHeadcount, 3000, null, FlowType.LEGACY, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    return buncheol.getId();
  }

  private Long createBuncheolMember(final Long buncheolId) {
    return createBuncheolMember(buncheolId, groupMemberId);
  }

  private Long createBuncheolMember(final Long buncheolId, final Long memberId) {
    BuncheolMember member = BuncheolMember.create(buncheolId, memberId, 30_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    em.flush();
    em.clear();
    return member.getId();
  }

  private Long insertShippingAddress(final Long userId, final String storeName) {
    jdbcTemplate.update(
        "INSERT INTO shipping_addresses (user_id, shipping_method, store_name)" + " VALUES (?, ?, ?)",
        userId,
        "GS25_HALF",
        storeName);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM shipping_addresses WHERE user_id = ? AND store_name = ?",
        Long.class,
        userId,
        storeName);
  }

  // 새 모델 컬럼(amount, refund_*, due_at, status, cancel_reason)으로 participation 한 건을 직접 삽입하고 id 를 반환한다.
  // shipping_fee 는 명시하지 않아 스키마 DEFAULT 0 으로 들어간다 (이 헬퍼는 배송비 시나리오를 다루지 않는다).
  private Long insertParticipation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long userId,
      final Long shippingAddressId,
      final long amount,
      final Instant dueAt,
      final ParticipationStatus status,
      final ParticipationCancelReason cancelReason) {
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, amount, refund_bank, refund_account, refund_holder,"
            + " due_at, status, cancel_reason)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        userId,
        shippingAddressId,
        amount,
        REFUND_ACCOUNT.bank(),
        REFUND_ACCOUNT.account(),
        REFUND_ACCOUNT.holder(),
        Timestamp.from(dueAt),
        status.name(),
        cancelReason == null ? null : cancelReason.name());
    return jdbcTemplate.queryForObject(
        "SELECT id FROM participations WHERE shipping_address_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        shippingAddressId);
  }

  /**
   * 자발 취소 CAS 가 실제로 통과시키는 상태가 {@link ParticipationCancellability} 의 판정과 일치하는지 (docs/56 S-1). 판정은
   * 참여 조회 응답이 그대로 내려가므로, 여기서 갈리면 "버튼은 보이는데 눌러도 실패"(또는 그 반대)가 그대로 사용자에게 드러난다. 어댑터가 공유 집합
   * 대신 자기 상태 목록을 다시 들면 이 테스트가 빨개진다.
   *
   * <p>⚠️ 고정하는 것은 <b>상태 축뿐</b>이다 — CAS 는 성사 확정 선후·플로우를 보지 않으므로(그쪽은 애플리케이션 게이트 단독) 이 테스트가
   * "판정 ≡ CAS" 를 뜻하지는 않는다.
   */
  @ParameterizedTest
  @EnumSource(ParticipationStatus.class)
  @DisplayName("자발 취소 CAS 가 통과시키는 상태가 취소 판정과 일치한다")
  void 자발_취소_CAS_가_판정과_같은_상태_집합을_쓴다(final ParticipationStatus status) {
    Long buncheolId = createBuncheol();
    Long memberId = createBuncheolMember(buncheolId);
    Long addressId = insertShippingAddress(participantId, "GS25 취소판정점");
    Long participationId =
        insertParticipation(
            buncheolId,
            memberId,
            participantId,
            addressId,
            30_000L,
            Instant.now().plus(1, ChronoUnit.DAYS),
            status,
            null);

    boolean cancelled = participationRepository.cancelByUserIfCancellable(participationId, Instant.now());

    assertThat(cancelled)
        .isEqualTo(ParticipationCancellability.cancellableStatuses().contains(status));
  }

  private String statusOf(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM participations WHERE id = ?", String.class, participationId);
  }

  private String cancelReasonOf(final Long participationId) {
    return jdbcTemplate.queryForObject(
        "SELECT cancel_reason FROM participations WHERE id = ?", String.class, participationId);
  }

  private String buncheolStatusOf(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private void insertConfirmedParticipation(
      final Long buncheolId, final Long slotId, final String storeName) {
    insertConfirmedParticipation(buncheolId, slotId, participantId, storeName);
  }

  private void insertConfirmedParticipation(
      final Long buncheolId, final Long slotId, final Long userId, final String storeName) {
    insertParticipation(
        buncheolId,
        slotId,
        userId,
        insertShippingAddress(userId, storeName),
        30_000L,
        Instant.now().plus(20, ChronoUnit.MINUTES),
        ParticipationStatus.CONFIRMED,
        null);
  }

  // saveIfRecruiting 은 운영(MySQL) 전용 raw JDBC 조건부 INSERT(UTC_TIMESTAMP() + RETURN_GENERATED_KEYS 단일키
  // 가정)라 H2 어댑터 테스트로 직접 검증하지 않는다. 선착순 슬롯 가드(PARTICIPATION_ALREADY_EXISTS)·모집중 조건은
  // active_member_id UNIQUE 제약과 도메인/서비스 레벨 테스트에서 다룬다. 아래 케이스들은 raw INSERT 픽스처로 조회·CAS 메서드를
  // 검증한다.

  @Nested
  @DisplayName("findAllByParticipantIdOrderByCreatedAtDesc")
  class FindAllByParticipantIdTest {

    @Test
    void 참여자_기준으로_모든_참여를_조회한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addrA = insertShippingAddress(participantId, "강남역점A");
      Long addrB = insertShippingAddress(participantId, "강남역점B");
      // CANCELLED 후 AWAITING_PAYMENT (active_member_id UNIQUE 제약 회피)
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addrA,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addrB,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      List<Participation> result =
          participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);

      assertThat(result).hasSize(2);
      assertThat(result).allMatch(p -> p.getParticipantId().equals(participantId));
    }

    @Test
    void 참여_내역이_없으면_빈_리스트를_반환한다() {
      List<Participation> result =
          participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsUnfinishedByParticipantId — 탈퇴 가드용 끝나지 않은 참여 존재 여부")
  class ExistsUnfinishedByParticipantIdTest {

    private Long insertConfirmed(final String storeName) {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      return insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          insertShippingAddress(participantId, storeName),
          30_000L,
          Instant.now().plus(20, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
    }

    /**
     * 배송 스냅샷을 그 참여의 <b>묶음</b>에 붙인다 — 탈퇴 가드가 배송을 묶음으로 찾기 때문이다
     * (택배 1개 = 묶음 1개). 참여에 묶음이 없으면 묶음을 하나 만들어 붙인 뒤 배송을 건다.
     */
    private void insertDelivery(final Long participationId, final String deliveryStatus) {
      Long bundleId = ensureBundle(participationId);
      jdbcTemplate.update(
          "INSERT INTO deliveries (participation_id, bundle_id, shipping_method, store_name,"
              + " receiver_nickname, receiver_phone_number, status)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?)",
          participationId,
          bundleId,
          "GS25_HALF",
          "매장",
          "닉네임",
          "01012345678",
          deliveryStatus);
    }

    private Long ensureBundle(final Long participationId) {
      Long existing =
          jdbcTemplate.queryForObject(
              "SELECT bundle_id FROM participations WHERE id = ?", Long.class, participationId);
      if (existing != null) {
        return existing;
      }
      jdbcTemplate.update(
          "INSERT INTO participation_bundles (buncheol_id, participant_id, shipping_fee,"
              + " refund_bank, refund_account, refund_holder)"
              + " SELECT p.buncheol_id, p.participant_id, 0, '국민', '12345678', '홍길동'"
              + " FROM participations p WHERE p.id = ?",
          participationId);
      Long bundleId =
          jdbcTemplate.queryForObject("SELECT MAX(id) FROM participation_bundles", Long.class);
      jdbcTemplate.update(
          "UPDATE participations SET bundle_id = ? WHERE id = ?", bundleId, participationId);
      return bundleId;
    }

    private void updatePaybackStatus(final Long participationId, final PaybackStatus status) {
      jdbcTemplate.update(
          "UPDATE participations SET payback_status = ? WHERE id = ?",
          status.name(),
          participationId);
    }

    @Test
    void 입금_확인_중인_참여가_있으면_true_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "활성매장");
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addr,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isTrue();
    }

    @Test
    void 취소된_참여만_있으면_false_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "취소매장");
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addr,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isFalse();
    }

    @Test
    void 입금확인됐지만_배송이_끝나지_않았으면_true_를_반환한다() {
      Long participationId = insertConfirmed("배송중매장");
      insertDelivery(participationId, "SHIPPING");

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isTrue();
    }

    @Test
    void 입금확인됐지만_배송_스냅샷이_없으면_true_를_반환한다() {
      insertConfirmed("스냅샷없음매장");

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isTrue();
    }

    @Test
    void 배송이_끝난_참여만_있으면_false_를_반환한다() {
      Long delivered = insertConfirmed("배송완료매장");
      insertDelivery(delivered, "DELIVERED");
      Long received = insertConfirmed("수령완료매장");
      insertDelivery(received, "RECEIVED");

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isFalse();
    }

    @Test
    void 배송이_끝났어도_환급_신청이_검수_대기_중이면_true_를_반환한다() {
      Long participationId = insertConfirmed("환급대기매장");
      insertDelivery(participationId, "RECEIVED");
      updatePaybackStatus(participationId, PaybackStatus.REQUESTED);

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isTrue();
    }

    @Test
    void 배송이_끝나고_환급이_완료되거나_반려됐으면_false_를_반환한다() {
      Long completed = insertConfirmed("환급완료매장");
      insertDelivery(completed, "RECEIVED");
      updatePaybackStatus(completed, PaybackStatus.COMPLETED);
      Long rejected = insertConfirmed("환급반려매장");
      insertDelivery(rejected, "RECEIVED");
      updatePaybackStatus(rejected, PaybackStatus.REJECTED);

      assertThat(participationRepository.existsUnfinishedByParticipantId(participantId)).isFalse();
    }
  }

  @Nested
  @DisplayName("existsActiveByBuncheolIdAndParticipantId — 분철당 중복 참여 가드")
  class ExistsActiveByBuncheolIdAndParticipantIdTest {

    @Test
    void 해당_분철에_활성_참여가_있으면_true_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          insertShippingAddress(participantId, "중복가드_활성"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(
              participationRepository.existsActiveByBuncheolIdAndParticipantId(
                  buncheolId, participantId))
          .isTrue();
    }

    @Test
    void 취소된_참여만_있으면_false_를_반환해_재참여가_열린다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          insertShippingAddress(participantId, "중복가드_취소"),
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.PAYMENT_TIMEOUT);

      assertThat(
              participationRepository.existsActiveByBuncheolIdAndParticipantId(
                  buncheolId, participantId))
          .isFalse();
    }

    @Test
    void 다른_분철의_활성_참여는_영향을_주지_않는다() {
      Long buncheolA = createBuncheol();
      Long buncheolB = createBuncheol();
      Long slotA = createBuncheolMember(buncheolA);
      insertParticipation(
          buncheolA,
          slotA,
          participantId,
          insertShippingAddress(participantId, "중복가드_타분철"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(
              participationRepository.existsActiveByBuncheolIdAndParticipantId(
                  buncheolB, participantId))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("분철당 참여자 유니크 — LEGACY 전용 (C2C 다슬롯 허용, docs/46 §7.1-11)")
  class ActiveParticipantUniqueGuardTest {

    @Test
    void C2C_참여는_같은_분철의_다른_멤버_슬롯에_중복_참여할_수_있다() {
      // C2C 는 flow_type 조건 때문에 legacy_active_participant_id 가 NULL 이라 유니크의 영향을 받지 않는다.
      // 같은 슬롯의 중복 점유는 여전히 uq_participations_active_member 가 차단한다.
      Long buncheolId = createBuncheol();
      Long slotA = createBuncheolMember(buncheolId);
      Long otherMember = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "중복가드멤버");
      Long slotB = createBuncheolMember(buncheolId, otherMember);
      insertC2cParticipation(
          buncheolId, slotA, participantId, insertShippingAddress(participantId, "유니크가드_1"));
      insertC2cParticipation(
          buncheolId, slotB, participantId, insertShippingAddress(participantId, "유니크가드_2"));

      assertThat(
              participationRepository.existsActiveByBuncheolIdAndParticipantId(
                  buncheolId, participantId))
          .isTrue();
    }

    @Test
    void LEGACY_참여는_같은_분철에_활성_참여를_중복_삽입하면_유니크_제약에_걸린다() {
      // 서비스 사전 체크의 check-then-insert 갭(동시 이중 요청)을
      // uq_participations_legacy_active_participant 가 최종 차단하는지 검증한다.
      Long buncheolId = createBuncheol();
      Long slotA = createBuncheolMember(buncheolId);
      Long otherMember = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "레거시가드멤버");
      Long slotB = createBuncheolMember(buncheolId, otherMember);
      insertParticipation(
          buncheolId,
          slotA,
          participantId,
          insertShippingAddress(participantId, "레거시가드_1"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThatThrownBy(
              () ->
                  insertParticipation(
                      buncheolId,
                      slotB,
                      participantId,
                      insertShippingAddress(participantId, "레거시가드_2"),
                      30_000L,
                      Instant.now().plus(30, ChronoUnit.MINUTES),
                      ParticipationStatus.AWAITING_PAYMENT,
                      null))
          .isInstanceOf(DuplicateKeyException.class);
    }

    // C2C 참여 삽입 헬퍼 — flow_type='C2C' 를 명시한다 (기본 헬퍼는 컬럼 DEFAULT 인 LEGACY 로 들어간다).
    private void insertC2cParticipation(
        final Long buncheolId, final Long buncheolMemberId, final Long userId, final Long addressId) {
      jdbcTemplate.update(
          "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
              + " shipping_address_id, amount, refund_bank, refund_account, refund_holder,"
              + " due_at, status, flow_type)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'C2C')",
          buncheolId,
          buncheolMemberId,
          userId,
          addressId,
          30_000L,
          REFUND_ACCOUNT.bank(),
          REFUND_ACCOUNT.account(),
          REFUND_ACCOUNT.holder(),
          Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)),
          ParticipationStatus.AWAITING_PAYMENT.name());
    }

    @Test
    void MySQL_형식_유니크_위반_메시지에서_인덱스명을_구분한다() {
      // saveIfRecruiting 은 운영(MySQL) 전용 raw SQL 이라 H2 로 직접 못 태우므로, DuplicateKey 를
      // 에러코드로 나누는 인덱스명 매칭만 실제 메시지 형식으로 검증한다.
      DuplicateKeyException mysqlStyle =
          new DuplicateKeyException(
              "PreparedStatementCallback; SQL [INSERT INTO participations ...];"
                  + " Duplicate entry '7-9' for key"
                  + " 'participations.uq_participations_active_participant'");

      assertThat(
              JpaParticipationRepositoryAdapter.isViolationOf(
                  mysqlStyle, "uq_participations_active_participant"))
          .isTrue();
      assertThat(
              JpaParticipationRepositoryAdapter.isViolationOf(
                  mysqlStyle, "uq_participations_active_member"))
          .isFalse();
    }

    @Test
    void H2_형식_대문자_메시지도_대소문자_무시로_매칭한다() {
      DuplicateKeyException h2Style =
          new DuplicateKeyException(
              "Unique index or primary key violation:"
                  + " \"PUBLIC.UQ_PARTICIPATIONS_ACTIVE_PARTICIPANT_INDEX_9 ON"
                  + " PUBLIC.PARTICIPATIONS(BUNCHEOL_ID, ACTIVE_PARTICIPANT_ID) VALUES (7, 9)\"");

      assertThat(
              JpaParticipationRepositoryAdapter.isViolationOf(
                  h2Style, "uq_participations_active_participant"))
          .isTrue();
    }

    @Test
    void 취소된_참여가_있으면_같은_분철에_다시_참여할_수_있다() {
      Long buncheolId = createBuncheol();
      Long slotA = createBuncheolMember(buncheolId);
      insertParticipation(
          buncheolId,
          slotA,
          participantId,
          insertShippingAddress(participantId, "재참여_취소분"),
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.PAYMENT_TIMEOUT);

      // 취소된 참여는 active_participant_id 가 NULL 이라 유니크에 걸리지 않는다.
      Long retryId =
          insertParticipation(
              buncheolId,
              slotA,
              participantId,
              insertShippingAddress(participantId, "재참여_활성분"),
              30_000L,
              Instant.now().plus(30, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      assertThat(retryId).isNotNull();
    }
  }

  @Nested
  @DisplayName("existsActiveByShippingAddressId — 배송지 삭제 가드용(활성만)")
  class ExistsActiveByShippingAddressIdTest {

    @Test
    void 배송지를_참조하는_활성_참여가_있으면_true_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "활성참여매장");
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addr,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.existsActiveByShippingAddressId(addr)).isTrue();
    }

    // 🔴 <b>PR-B 이후의 실제 데이터 모양이다.</b> 참여 INSERT 에서 배송지를 빼면 신규 행의 사본은 항상
    // NULL 이고 주소는 묶음에만 있다. 이때 가드가 사본을 보면 <b>전 건 false</b> — 배송 대기 중인
    // 배송지가 사용자 손으로 지워지고, ON DELETE SET NULL 이 정본까지 비운다(updatable=false 라 복구 불가).
    @Test
    void 참여_사본이_NULL_이어도_묶음이_배송지를_가지면_true_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "묶음만매장");
      Long participationId =
          insertParticipation(
              buncheolId,
              buncheolMemberId,
              participantId,
              addr,
              30_000L,
              Instant.now().plus(30, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);
      attachBundleWithAddress(participationId, addr);
      // 신규 행의 실제 모양 — 사본은 비어 있고 주소는 묶음에만 있다.
      jdbcTemplate.update(
          "UPDATE participations SET shipping_address_id = NULL WHERE id = ?", participationId);

      assertThat(participationRepository.existsActiveByShippingAddressId(addr)).isTrue();
    }

    // 미연결 옛 행(P2-b 배포선 창)은 사본으로만 찾을 수 있다. OR 폴백을 고정한다 — 가드는 비대칭이라
    // 과탐(못 지움)은 재시도로 끝나지만 미탐은 비가역이다.
    @Test
    void 묶음_없는_옛_행도_사본으로_보호된다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "미연결옛행매장");
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addr,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.existsActiveByShippingAddressId(addr)).isTrue();
    }

    /** 묶음을 만들어 붙이고 그 묶음에 배송지를 심는다. */
    private void attachBundleWithAddress(final Long participationId, final Long addressId) {
      jdbcTemplate.update(
          "INSERT INTO participation_bundles (buncheol_id, participant_id, shipping_address_id,"
              + " shipping_fee, refund_bank, refund_account, refund_holder)"
              + " SELECT p.buncheol_id, p.participant_id, ?, 0, '국민', '12345678', '홍길동'"
              + " FROM participations p WHERE p.id = ?",
          addressId,
          participationId);
      Long bundleId =
          jdbcTemplate.queryForObject("SELECT MAX(id) FROM participation_bundles", Long.class);
      jdbcTemplate.update(
          "UPDATE participations SET bundle_id = ? WHERE id = ?", bundleId, participationId);
    }

    @Test
    void 배송지를_참조하는_참여가_취소된_것뿐이면_false_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "취소된참여매장");
      insertParticipation(
          buncheolId,
          buncheolMemberId,
          participantId,
          addr,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      assertThat(participationRepository.existsActiveByShippingAddressId(addr)).isFalse();
    }
  }

  @Nested
  @DisplayName("FK ON DELETE SET NULL — 종료된 참여가 참조하던 배송지 삭제")
  class ShippingAddressDeleteSetNullTest {

    @Test
    void 취소된_참여가_참조하던_배송지를_삭제하면_참여의_shipping_address_id_가_NULL_이_된다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "삭제될매장");
      Long pid =
          insertParticipation(
              buncheolId,
              buncheolMemberId,
              participantId,
              addr,
              30_000L,
              Instant.now(),
              ParticipationStatus.CANCELLED,
              ParticipationCancelReason.BUNCHEOL_CANCELLED);

      jdbcTemplate.update("DELETE FROM shipping_addresses WHERE id = ?", addr);

      Long addrAfter =
          jdbcTemplate.queryForObject(
              "SELECT shipping_address_id FROM participations WHERE id = ?", Long.class, pid);
      assertThat(addrAfter).isNull();
    }
  }

  @Nested
  @DisplayName("countActiveByBuncheolIds — JPQL constructor expression 검증")
  class CountActiveByBuncheolIdsTest {

    @Test
    void 활성_상태_참여를_분철_단위로_집계한다() {
      Long buncheolA = createBuncheol();
      Long buncheolB = createBuncheol();
      Long bmA = createBuncheolMember(buncheolA);
      Long bmB = createBuncheolMember(buncheolB);
      Long addrA = insertShippingAddress(participantId, "분철A_매장");
      Long addrB = insertShippingAddress(participantId, "분철B_매장");
      Long addrB2 = insertShippingAddress(participantId, "분철B_매장2");

      // A: AWAITING_PAYMENT 1건
      insertParticipation(
          buncheolA,
          bmA,
          participantId,
          addrA,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      // B: CANCELLED 1건 + CONFIRMED 1건 → 활성은 1
      insertParticipation(
          buncheolB,
          bmB,
          participantId,
          addrB,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);
      insertParticipation(
          buncheolB,
          bmB,
          participantId,
          addrB2,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);

      List<BuncheolActiveParticipationCount> result =
          participationRepository.countActiveByBuncheolIds(List.of(buncheolA, buncheolB));

      Map<Long, Long> byId =
          result.stream()
              .collect(
                  Collectors.toMap(
                      BuncheolActiveParticipationCount::buncheolId,
                      BuncheolActiveParticipationCount::count));
      assertThat(byId.get(buncheolA)).isEqualTo(1L);
      assertThat(byId.get(buncheolB)).isEqualTo(1L);
    }

    @Test
    void 빈_입력에는_빈_리스트를_반환한다() {
      List<BuncheolActiveParticipationCount> result =
          participationRepository.countActiveByBuncheolIds(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    void 활성_참여가_없는_분철은_결과에_포함되지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "단일매장");
      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addr,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      List<BuncheolActiveParticipationCount> result =
          participationRepository.countActiveByBuncheolIds(List.of(buncheolId));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("countConfirmedByBuncheolIds — JPQL constructor expression 검증 (docs/56 S-2)")
  class CountConfirmedByBuncheolIdsTest {

    @Test
    void 입금확인_참여만_분철_단위로_집계한다() {
      Long buncheolA = createBuncheol();
      Long buncheolB = createBuncheol();
      Long bmA = createBuncheolMember(buncheolA);
      Long bmB = createBuncheolMember(buncheolB);
      // 활성 참여는 슬롯당 1건(uq_participations_active_member)이라 B 에는 슬롯을 하나 더 판다.
      Long otherMember = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "확정집계멤버");
      Long bmB2 = createBuncheolMember(buncheolB, otherMember);
      Long addrA = insertShippingAddress(participantId, "확정A_매장");
      Long addrB = insertShippingAddress(participantId, "확정B_매장");
      Long addrB2 = insertShippingAddress(secondParticipantId, "확정B_매장2");

      insertParticipation(
          buncheolA,
          bmA,
          participantId,
          addrA,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
      // B: 보냈어요는 개최자 확인 전이라 세면 안 된다 (docs/56 §21-2 — 허위 마킹으로 개최를 잠그지 않는다).
      insertParticipation(
          buncheolB,
          bmB,
          participantId,
          addrB,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.PAYMENT_SENT,
          null);
      insertParticipation(
          buncheolB,
          bmB2,
          secondParticipantId,
          addrB2,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);

      Map<Long, Long> byId =
          participationRepository.countConfirmedByBuncheolIds(List.of(buncheolA, buncheolB)).stream()
              .collect(
                  Collectors.toMap(
                      BuncheolConfirmedParticipationCount::buncheolId,
                      BuncheolConfirmedParticipationCount::count));

      assertThat(byId.get(buncheolA)).isEqualTo(1L);
      assertThat(byId.get(buncheolB)).isEqualTo(1L);
    }

    @Test
    void 빈_입력에는_쿼리_없이_빈_리스트를_반환한다() {
      assertThat(participationRepository.countConfirmedByBuncheolIds(List.of())).isEmpty();
    }

    @Test
    void 입금확인_참여가_없는_분철은_결과에_포함되지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "확정없음매장");
      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addr,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.countConfirmedByBuncheolIds(List.of(buncheolId))).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveBuncheolMemberIds — 여러 분철의 점유 슬롯 ID 조회")
  class FindActiveBuncheolMemberIdsTest {

    @Test
    void 활성_참여가_점유한_슬롯_ID만_반환하고_취소된_슬롯은_제외한다() {
      Long buncheolA = createBuncheol();
      Long buncheolB = createBuncheol();
      Long takenSlotA = createBuncheolMember(buncheolA);
      Long takenSlotB = createBuncheolMember(buncheolB);
      Long otherMemberB = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "취소슬롯멤버");
      Long cancelledSlotB = createBuncheolMember(buncheolB, otherMemberB);
      insertParticipation(
          buncheolA,
          takenSlotA,
          participantId,
          insertShippingAddress(participantId, "활성슬롯A"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          buncheolB,
          takenSlotB,
          participantId,
          insertShippingAddress(participantId, "확정슬롯B"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
      insertParticipation(
          buncheolB,
          cancelledSlotB,
          participantId,
          insertShippingAddress(participantId, "취소슬롯B"),
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      List<Long> result =
          participationRepository.findActiveBuncheolMemberIds(List.of(buncheolA, buncheolB));

      assertThat(result).containsExactlyInAnyOrder(takenSlotA, takenSlotB);
    }

    @Test
    void 빈_입력에는_빈_리스트를_반환한다() {
      assertThat(participationRepository.findActiveBuncheolMemberIds(List.of())).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveByBuncheolId — 분철 단위 활성 참여 전체 조회")
  class FindActiveByBuncheolIdTest {

    @Test
    void AWAITING_PAYMENT_과_CONFIRMED_를_모두_포함하고_CANCELLED_는_제외한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "다른멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long otherParticipantId = TestUserFixture.insertUser(jdbcTemplate, "other_xx");
      Long addrA = insertShippingAddress(participantId, "주매장A");
      Long addrB = insertShippingAddress(otherParticipantId, "타매장B");
      Long addrC = insertShippingAddress(otherParticipantId, "타매장C");

      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addrA,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          buncheolId,
          bmId2,
          otherParticipantId,
          addrB,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
      // CANCELLED 는 제외돼야 한다 (같은 슬롯 bmId 에 비활성으로 추가)
      insertParticipation(
          buncheolId,
          bmId,
          otherParticipantId,
          addrC,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      List<Participation> result = participationRepository.findActiveByBuncheolId(buncheolId);

      assertThat(result)
          .extracting(Participation::getStatus)
          .containsExactlyInAnyOrder(
              ParticipationStatus.AWAITING_PAYMENT, ParticipationStatus.CONFIRMED);
    }

    @Test
    void 다른_분철의_참여는_포함하지_않는다() {
      Long targetId = createBuncheol();
      Long otherId = createBuncheol();
      Long targetMemberId = createBuncheolMember(targetId);
      Long otherMemberId = createBuncheolMember(otherId);
      Long addrTarget = insertShippingAddress(participantId, "타겟매장");
      Long addrOther = insertShippingAddress(participantId, "다른매장");

      insertParticipation(
          targetId,
          targetMemberId,
          participantId,
          addrTarget,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          otherId,
          otherMemberId,
          participantId,
          addrOther,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      List<Participation> result = participationRepository.findActiveByBuncheolId(targetId);

      assertThat(result)
          .singleElement()
          .satisfies(p -> assertThat(p.getBuncheolId()).isEqualTo(targetId));
    }

    @Test
    void 활성_참여가_없으면_빈_리스트를_반환한다() {
      Long buncheolId = createBuncheol();

      List<Participation> result = participationRepository.findActiveByBuncheolId(buncheolId);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveParticipantIdsByBuncheolIdForUpdate — 활성 참여자 id 잠금 조회 (C2C 정원 충족 판정)")
  class FindActiveParticipantIdsForUpdateTest {

    @Test
    void 활성_상태만_세고_CANCELLED_는_제외한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "다른멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long otherParticipantId = TestUserFixture.insertUser(jdbcTemplate, "other_cnt_xx");
      Long addrA = insertShippingAddress(participantId, "주매장A");
      Long addrB = insertShippingAddress(otherParticipantId, "타매장B");
      Long addrC = insertShippingAddress(otherParticipantId, "타매장C");

      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addrA,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          buncheolId,
          bmId2,
          otherParticipantId,
          addrB,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
      insertParticipation(
          buncheolId,
          bmId,
          otherParticipantId,
          addrC,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      List<Long> participantIds =
          participationRepository.findActiveParticipantIdsByBuncheolIdForUpdate(buncheolId);

      assertThat(participantIds).containsExactlyInAnyOrder(participantId, otherParticipantId);
    }

    @Test
    void 활성_참여가_없으면_빈_리스트를_반환한다() {
      Long buncheolId = createBuncheol();

      List<Long> participantIds =
          participationRepository.findActiveParticipantIdsByBuncheolIdForUpdate(buncheolId);

      assertThat(participantIds).isEmpty();
    }
  }

  @Nested
  @DisplayName("findConfirmedByBuncheolId / countConfirmedByBuncheolId — 입금확인 참여만")
  class ConfirmedByBuncheolIdTest {

    @Test
    void CONFIRMED_참여만_조회하고_AWAITING_PAYMENT_은_제외한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "확정멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrConfirmed = insertShippingAddress(participantId, "확정매장");
      Long addrAwaiting = insertShippingAddress(secondParticipantId, "대기매장");

      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addrConfirmed,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);
      insertParticipation(
          buncheolId,
          bmId2,
          secondParticipantId,
          addrAwaiting,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.findConfirmedByBuncheolId(buncheolId))
          .singleElement()
          .satisfies(p -> assertThat(p.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED));
      assertThat(participationRepository.countConfirmedByBuncheolId(buncheolId)).isEqualTo(1);
    }

    @Test
    void 확정_참여가_없으면_빈_리스트와_0_을_반환한다() {
      Long buncheolId = createBuncheol();

      assertThat(participationRepository.findConfirmedByBuncheolId(buncheolId)).isEmpty();
      assertThat(participationRepository.countConfirmedByBuncheolId(buncheolId)).isZero();
    }
  }

  @Nested
  @DisplayName("findCancelledByBuncheolId — 취소된 참여만")
  class CancelledByBuncheolIdTest {

    @Test
    void 취소된_참여만_조회하고_활성_참여는_제외한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "취소멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long cancelledId =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              insertShippingAddress(participantId, "취소매장"),
              30_000L,
              Instant.now().plus(30, ChronoUnit.MINUTES),
              ParticipationStatus.CANCELLED,
              ParticipationCancelReason.BUNCHEOL_CANCELLED);
      insertParticipation(
          buncheolId,
          bmId2,
          secondParticipantId,
          insertShippingAddress(secondParticipantId, "확정매장"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);

      assertThat(participationRepository.findCancelledByBuncheolId(buncheolId))
          .extracting(Participation::getId)
          .containsExactly(cancelledId);
    }

    @Test
    void 다른_분철의_취소분은_섞이지_않는다() {
      Long buncheolId = createBuncheol();
      Long otherBuncheolId = createBuncheol();
      insertParticipation(
          otherBuncheolId,
          createBuncheolMember(otherBuncheolId),
          participantId,
          insertShippingAddress(participantId, "다른분철매장"),
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      assertThat(participationRepository.findCancelledByBuncheolId(buncheolId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findOverduePaymentTargets — 입금 만료 스케줄러 폴링")
  class FindOverduePaymentTargetsTest {

    @Test
    void AWAITING_PAYMENT_이고_기한이_지난_건만_조회한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "만료멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrOverdue = insertShippingAddress(participantId, "만료매장");
      Long addrFuture = insertShippingAddress(secondParticipantId, "미래매장");
      Instant now = Instant.now();

      Long overdueId =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addrOverdue,
              30_000L,
              now.minus(5, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);
      // 기한이 아직 안 지난 건은 제외
      insertParticipation(
          buncheolId,
          bmId2,
          secondParticipantId,
          addrFuture,
          30_000L,
          now.plus(20, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      List<Participation> result = participationRepository.findOverduePaymentTargets(now, 100);

      assertThat(result).extracting(Participation::getId).containsExactly(overdueId);
    }

    // 🔴 C2C 는 기한이 지나도 자동 취소하지 않는다 (docs/70 결정 9). 기한은 "개최자가 「제외」로 정리에
    // 나설 수 있는 시각"이지 취소 시각이 아니다.
    @Test
    void C2C_참여는_기한이_지나도_조회되지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "C2C멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrLegacy = insertShippingAddress(participantId, "레거시매장");
      Long addrC2c = insertShippingAddress(secondParticipantId, "C2C매장");
      Instant now = Instant.now();

      Long legacyOverdueId =
          insertParticipation(
              buncheolId, bmId, participantId, addrLegacy, 30_000L,
              now.minus(5, ChronoUnit.MINUTES), ParticipationStatus.AWAITING_PAYMENT, null);
      Long c2cOverdueId =
          insertParticipation(
              buncheolId, bmId2, secondParticipantId, addrC2c, 30_000L,
              now.minus(5, ChronoUnit.MINUTES), ParticipationStatus.AWAITING_PAYMENT, null);
      jdbcTemplate.update(
          "UPDATE participations SET flow_type = 'C2C' WHERE id = ?", c2cOverdueId);

      List<Participation> result = participationRepository.findOverduePaymentTargets(now, 100);

      // 같은 조건(AWAITING_PAYMENT + 기한 경과)인데 LEGACY 만 잡힌다.
      assertThat(result).extracting(Participation::getId).containsExactly(legacyOverdueId);
    }

    @Test
    void 확정된_건은_기한이_지나도_조회되지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "확정만료매장");
      Instant now = Instant.now();
      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addr,
          30_000L,
          now.minus(5, ChronoUnit.MINUTES),
          ParticipationStatus.CONFIRMED,
          null);

      assertThat(participationRepository.findOverduePaymentTargets(now, 100)).isEmpty();
    }

    @Test
    void limit_을_초과하지_않는다() {
      Long buncheolId = createBuncheol();
      Long m1 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "리밋멤버1");
      Long m2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "리밋멤버2");
      Long bm1 = createBuncheolMember(buncheolId, m1);
      Long bm2 = createBuncheolMember(buncheolId, m2);
      Instant now = Instant.now();
      insertParticipation(
          buncheolId,
          bm1,
          participantId,
          insertShippingAddress(participantId, "리밋매장1"),
          30_000L,
          now.minus(5, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          buncheolId,
          bm2,
          secondParticipantId,
          insertShippingAddress(secondParticipantId, "리밋매장2"),
          30_000L,
          now.minus(5, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      assertThat(participationRepository.findOverduePaymentTargets(now, 1)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("confirmPaymentIfAwaiting — 호스트 입금확인 CAS")
  class ConfirmPaymentIfAwaitingTest {

    @Test
    void AWAITING_PAYMENT_이고_기한_내면_CONFIRMED_로_전이한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "입금확인매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      boolean confirmed = participationRepository.confirmPaymentIfAwaiting(pid, now);

      assertThat(confirmed).isTrue();
      assertThat(statusOf(pid)).isEqualTo("CONFIRMED");
    }

    @Test
    void 입금_기한과_정확히_같은_시각이면_확정된다() {
      // dueAt == now 경계: 가드가 dueAt >= now 라 마지막 순간까지 확정을 허용한다.
      // H2 TIMESTAMP/Instant 정밀도 차이로 인한 flaky 를 막으려 밀리초로 절삭한 동일 값을 양쪽에 쓴다.
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "기한경계매장");
      Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now,
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      assertThat(participationRepository.confirmPaymentIfAwaiting(pid, now)).isTrue();
      assertThat(statusOf(pid)).isEqualTo("CONFIRMED");
    }

    @Test
    void 입금_기한이_지났으면_전이하지_않고_false_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "기한경과매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.minus(1, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      boolean confirmed = participationRepository.confirmPaymentIfAwaiting(pid, now);

      assertThat(confirmed).isFalse();
      assertThat(statusOf(pid)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 이미_취소된_건은_전이하지_않고_false_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "취소된확인매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.CANCELLED,
              ParticipationCancelReason.BUNCHEOL_CANCELLED);

      assertThat(participationRepository.confirmPaymentIfAwaiting(pid, now)).isFalse();
    }
  }

  @Nested
  @DisplayName("expirePaymentIfOverdue — 입금 만료 CAS")
  class ExpireIfOverdueTest {

    @Test
    void AWAITING_PAYMENT_이고_기한이_지났으면_PAYMENT_TIMEOUT_으로_전이한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "만료처리매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.minus(1, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      boolean expired = participationRepository.expirePaymentIfOverdue(pid, now);

      assertThat(expired).isTrue();
      assertThat(statusOf(pid)).isEqualTo("CANCELLED");
      assertThat(cancelReasonOf(pid)).isEqualTo("PAYMENT_TIMEOUT");
    }

    @Test
    void 기한이_지나지_않았으면_전이하지_않고_false_를_반환한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "기한전매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.plus(10, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      assertThat(participationRepository.expirePaymentIfOverdue(pid, now)).isFalse();
      assertThat(statusOf(pid)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void 호스트가_먼저_확정했으면_만료_전이는_실패한다() {
      // 호스트 확인 vs 만료 스케줄러 race — 확정이 이긴 뒤 만료가 들어오면 CAS 실패.
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addr = insertShippingAddress(participantId, "race매장");
      Instant now = Instant.now();
      Long pid =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addr,
              30_000L,
              now.minus(1, ChronoUnit.MINUTES),
              ParticipationStatus.CONFIRMED,
              null);

      assertThat(participationRepository.expirePaymentIfOverdue(pid, now)).isFalse();
    }
  }

  @Nested
  @DisplayName("cancelActiveByBuncheolId — 분철 취소 시 활성 참여 일괄 전이")
  class CancelActiveByBuncheolIdTest {

    @Test
    void 활성_참여_AWAITING_PAYMENT_과_CONFIRMED_모두_BUNCHEOL_CANCELLED_로_전이된다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "일괄멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrAwaiting = insertShippingAddress(participantId, "대기일괄");
      Long addrConfirmed = insertShippingAddress(secondParticipantId, "확정일괄");
      Long awaitingId =
          insertParticipation(
              buncheolId,
              bmId,
              participantId,
              addrAwaiting,
              30_000L,
              Instant.now().plus(30, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);
      Long confirmedId =
          insertParticipation(
              buncheolId,
              bmId2,
              secondParticipantId,
              addrConfirmed,
              30_000L,
              Instant.now().plus(30, ChronoUnit.MINUTES),
              ParticipationStatus.CONFIRMED,
              null);

      int affected = participationRepository.cancelActiveByBuncheolId(buncheolId, Instant.now());

      assertThat(affected).isEqualTo(2);
      assertThat(statusOf(awaitingId)).isEqualTo("CANCELLED");
      assertThat(statusOf(confirmedId)).isEqualTo("CANCELLED");
      assertThat(cancelReasonOf(awaitingId)).isEqualTo("BUNCHEOL_CANCELLED");
      assertThat(cancelReasonOf(confirmedId)).isEqualTo("BUNCHEOL_CANCELLED");
    }

    @Test
    void 이미_취소된_참여는_영향받지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addrCancelled = insertShippingAddress(participantId, "이미취소");
      insertParticipation(
          buncheolId,
          bmId,
          participantId,
          addrCancelled,
          30_000L,
          Instant.now(),
          ParticipationStatus.CANCELLED,
          ParticipationCancelReason.BUNCHEOL_CANCELLED);

      int affected = participationRepository.cancelActiveByBuncheolId(buncheolId, Instant.now());

      assertThat(affected).isZero();
    }

    @Test
    void 다른_분철의_활성_참여는_영향받지_않는다() {
      Long target = createBuncheol();
      Long other = createBuncheol();
      Long targetBmId = createBuncheolMember(target);
      Long otherBmId = createBuncheolMember(other);
      Long addrTarget = insertShippingAddress(participantId, "타겟일괄");
      Long addrOther = insertShippingAddress(participantId, "다른일괄");
      insertParticipation(
          target,
          targetBmId,
          participantId,
          addrTarget,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
      insertParticipation(
          other,
          otherBmId,
          participantId,
          addrOther,
          30_000L,
          Instant.now().plus(30, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      int affected = participationRepository.cancelActiveByBuncheolId(target, Instant.now());

      assertThat(affected).isEqualTo(1);
      assertThat(participationRepository.findActiveByBuncheolId(other)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("confirmIfAllSlotsConfirmed — 전 슬롯 입금확인 시 조기 진행확정 CAS")
  class ConfirmIfAllSlotsConfirmedTest {

    @Test
    void 전_슬롯이_CONFIRMED_이고_minHeadcount_충족이면_CONFIRMED_로_전이한다() {
      Long buncheolId = createBuncheol(1); // 슬롯 2, minHeadcount 1
      Long slot1 = createBuncheolMember(buncheolId);
      Long member2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "확정멤버2");
      Long slot2 = createBuncheolMember(buncheolId, member2);
      insertConfirmedParticipation(buncheolId, slot1, "확정매장1");
      insertConfirmedParticipation(buncheolId, slot2, secondParticipantId, "확정매장2");

      int updated = buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, 2, Instant.now());

      assertThat(updated).isEqualTo(1);
      assertThat(buncheolStatusOf(buncheolId)).isEqualTo("CONFIRMED");
    }

    @Test
    void 일부_슬롯만_CONFIRMED면_전이하지_않는다() {
      Long buncheolId = createBuncheol(1);
      Long slot1 = createBuncheolMember(buncheolId);
      Long member2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "대기멤버2");
      Long slot2 = createBuncheolMember(buncheolId, member2);
      insertConfirmedParticipation(buncheolId, slot1, "확정매장1");
      insertParticipation(
          buncheolId,
          slot2,
          secondParticipantId,
          insertShippingAddress(secondParticipantId, "대기매장2"),
          30_000L,
          Instant.now().plus(20, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      int updated = buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, 2, Instant.now());

      assertThat(updated).isZero();
      assertThat(buncheolStatusOf(buncheolId)).isEqualTo("RECRUITING");
    }

    @Test
    void 전_슬롯_CONFIRMED_이라도_슬롯수가_minHeadcount_미만이면_전이하지_않는다() {
      Long buncheolId = createBuncheol(3); // 슬롯 2 < minHeadcount 3
      Long slot1 = createBuncheolMember(buncheolId);
      Long member2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "확정멤버2");
      Long slot2 = createBuncheolMember(buncheolId, member2);
      insertConfirmedParticipation(buncheolId, slot1, "확정매장1");
      insertConfirmedParticipation(buncheolId, slot2, secondParticipantId, "확정매장2");

      int updated = buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, 2, Instant.now());

      assertThat(updated).isZero();
      assertThat(buncheolStatusOf(buncheolId)).isEqualTo("RECRUITING");
    }
  }

  @Nested
  @DisplayName("배송비 환급 테스트")
  class PaybackTest {

    private static final String TWEET_URL = "https://x.com/fan/status/1234567890";

    // 확정 참여 두 건(서로 다른 유저)을 깔고 id 쌍을 반환한다.
    private Long[] insertTwoConfirmedParticipations() {
      Long buncheolId = createBuncheol();
      Long slot1 = createBuncheolMember(buncheolId);
      Long member2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "환급멤버2");
      Long slot2 = createBuncheolMember(buncheolId, member2);
      Long shipping1 = insertShippingAddress(participantId, "환급매장1");
      Long shipping2 = insertShippingAddress(secondParticipantId, "환급매장2");
      Long first =
          insertParticipation(
              buncheolId,
              slot1,
              participantId,
              shipping1,
              30_000L,
              Instant.now().plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.CONFIRMED,
              null);
      Long second =
          insertParticipation(
              buncheolId,
              slot2,
              secondParticipantId,
              shipping2,
              30_000L,
              Instant.now().plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.CONFIRMED,
              null);
      return new Long[] {first, second};
    }

    private Participation requestPayback(final Long participationId, final String tweetUrl) {
      Participation participation = participationRepository.findById(participationId).orElseThrow();
      participation.requestPayback(PaybackTweetUrl.parse(tweetUrl), Instant.now(), fees);
      participationRepository.savePaybackRequest(participation);
      return participation;
    }

    @Test
    void 환급_신청을_저장하면_상태와_스냅샷이_DB_에_반영된다() {
      Long[] ids = insertTwoConfirmedParticipations();

      requestPayback(ids[0], TWEET_URL);
      em.clear();

      Participation saved = participationRepository.findById(ids[0]).orElseThrow();
      assertThat(saved.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(saved.getPaybackTweetUrl()).isEqualTo(TWEET_URL);
      assertThat(saved.getPaybackAmount()).isEqualTo(saved.getShippingFee());
    }

    @Test
    void 같은_트윗_URL_로_다른_참여가_신청하면_유니크_위반이_중복_에러로_변환된다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);

      Participation second = participationRepository.findById(ids[1]).orElseThrow();
      second.requestPayback(PaybackTweetUrl.parse(TWEET_URL), Instant.now(), fees);

      assertThatThrownBy(() -> participationRepository.savePaybackRequest(second))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE);
    }

    @Test
    void 트윗_URL_중복_사전_체크는_본인_참여를_제외한다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);

      assertThat(participationRepository.existsPaybackTweetUrlUsedByOther(TWEET_URL, ids[1]))
          .isTrue();
      assertThat(participationRepository.existsPaybackTweetUrlUsedByOther(TWEET_URL, ids[0]))
          .isFalse();
      assertThat(
              participationRepository.existsPaybackTweetUrlUsedByOther(
                  "https://x.com/fan/status/999", ids[1]))
          .isFalse();
    }

    @Test
    void 확인중_신청은_완료_CAS_로_COMPLETED_전이되고_완료_시각이_기록된다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);
      Instant completedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

      boolean transitioned =
          participationRepository.completePaybackIfRequested(ids[0], completedAt);
      em.clear();

      assertThat(transitioned).isTrue();
      Participation completed = participationRepository.findById(ids[0]).orElseThrow();
      assertThat(completed.getPaybackStatus()).isEqualTo(PaybackStatus.COMPLETED);
      assertThat(completed.getPaybackCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void 신청_전이거나_이미_완료된_신청은_완료_CAS_가_false_를_반환한다() {
      Long[] ids = insertTwoConfirmedParticipations();

      // 신청 전(NONE)
      assertThat(participationRepository.completePaybackIfRequested(ids[0], Instant.now()))
          .isFalse();

      // 이미 완료된 뒤 중복 완료 (더블클릭 레이스의 순차 등가) — 두 번째는 실패해 알림 중복 발행이 차단된다
      requestPayback(ids[0], TWEET_URL);
      participationRepository.completePaybackIfRequested(ids[0], Instant.now());
      assertThat(participationRepository.completePaybackIfRequested(ids[0], Instant.now()))
          .isFalse();
    }

    @Test
    void 확인중_신청은_반려_CAS_로_REJECTED_전이되고_사유가_저장된다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);

      boolean transitioned =
          participationRepository.rejectPaybackIfRequested(ids[0], "비공개 계정이라 확인 불가", Instant.now());
      em.clear();

      Participation rejected = participationRepository.findById(ids[0]).orElseThrow();
      assertThat(transitioned).isTrue();
      assertThat(rejected.getPaybackStatus()).isEqualTo(PaybackStatus.REJECTED);
      assertThat(rejected.getPaybackRejectReason()).isEqualTo("비공개 계정이라 확인 불가");
    }

    @Test
    void 이미_완료된_신청은_반려_CAS_가_false_를_반환한다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);
      participationRepository.completePaybackIfRequested(ids[0], Instant.now());

      assertThat(participationRepository.rejectPaybackIfRequested(ids[0], "사유", Instant.now()))
          .isFalse();
    }

    @Test
    void 반려된_신청을_재신청하면_REQUESTED_로_돌아오고_반려_사유가_초기화된다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);
      participationRepository.rejectPaybackIfRequested(ids[0], "비공개 계정", Instant.now());
      em.clear();

      requestPayback(ids[0], "https://x.com/fan/status/456");
      em.clear();

      Participation retried = participationRepository.findById(ids[0]).orElseThrow();
      assertThat(retried.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(retried.getPaybackRejectReason()).isNull();
    }

    @Test
    void 완료된_신청은_재신청할_수_없다() {
      Long[] ids = insertTwoConfirmedParticipations();
      requestPayback(ids[0], TWEET_URL);
      participationRepository.completePaybackIfRequested(ids[0], Instant.now());
      em.clear();

      Participation completed = participationRepository.findById(ids[0]).orElseThrow();
      assertThatThrownBy(
              () ->
                  completed.requestPayback(
                      PaybackTweetUrl.parse("https://x.com/fan/status/456"), Instant.now(), fees))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("C2C 보냈어요 마킹/해제 CAS — paymentRejectedAt (docs/53 Q-03)")
  class PaymentSentRejectionTest {

    private Instant rejectedAtOf(final Long participationId) {
      Timestamp value =
          jdbcTemplate.queryForObject(
              "SELECT payment_rejected_at FROM participations WHERE id = ?",
              Timestamp.class,
              participationId);
      return value == null ? null : value.toInstant();
    }

    private Long insertSentParticipation() {
      Long buncheolId = createBuncheol();
      Long slotId = createBuncheolMember(buncheolId);
      Long id =
          insertParticipation(
              buncheolId,
              slotId,
              participantId,
              insertShippingAddress(participantId, "GS25 강남점"),
              30_000L,
              Instant.now().plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);
      participationRepository.markPaymentSentIfAwaiting(id, Instant.now());
      em.clear();
      return id;
    }

    @Test
    void 개최자_반려는_반려_시각을_기록한다() {
      Long id = insertSentParticipation();
      Instant rejectedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      Instant newDueAt = Instant.now().plus(24, ChronoUnit.HOURS);

      boolean applied =
          participationRepository.revertPaymentSentIfSent(id, newDueAt, rejectedAt, Instant.now());
      em.clear();

      assertThat(applied).isTrue();
      assertThat(statusOf(id)).isEqualTo(ParticipationStatus.AWAITING_PAYMENT.name());
      assertThat(rejectedAtOf(id)).isEqualTo(rejectedAt);
    }

    @Test
    void 참여자_셀프_철회는_반려_시각을_남기지_않는다() {
      Long id = insertSentParticipation();

      boolean applied =
          participationRepository.revertPaymentSentIfSent(
              id, Instant.now().plus(20, ChronoUnit.MINUTES), null, Instant.now());
      em.clear();

      assertThat(applied).isTrue();
      assertThat(statusOf(id)).isEqualTo(ParticipationStatus.AWAITING_PAYMENT.name());
      assertThat(rejectedAtOf(id)).isNull();
    }

    // 반려 → 재마킹 → 셀프 철회 후에도 반려 표시가 남으면 참여자에게 잘못된 안내가 나간다.
    @Test
    void 재마킹하면_반려_시각이_초기화된다() {
      Long id = insertSentParticipation();
      participationRepository.revertPaymentSentIfSent(
          id, Instant.now().plus(24, ChronoUnit.HOURS), Instant.now(), Instant.now());
      em.clear();
      assertThat(rejectedAtOf(id)).isNotNull();

      participationRepository.markPaymentSentIfAwaiting(id, Instant.now());
      em.clear();

      assertThat(statusOf(id)).isEqualTo(ParticipationStatus.PAYMENT_SENT.name());
      assertThat(rejectedAtOf(id)).isNull();
    }

    @Test
    void 보냈어요_상태가_아니면_해제_CAS_가_적용되지_않는다() {
      Long buncheolId = createBuncheol();
      Long slotId = createBuncheolMember(buncheolId);
      Long id =
          insertParticipation(
              buncheolId,
              slotId,
              participantId,
              insertShippingAddress(participantId, "GS25 역삼점"),
              30_000L,
              Instant.now().plus(20, ChronoUnit.MINUTES),
              ParticipationStatus.AWAITING_PAYMENT,
              null);

      boolean applied =
          participationRepository.revertPaymentSentIfSent(
              id, Instant.now().plus(24, ChronoUnit.HOURS), Instant.now(), Instant.now());
      em.clear();

      assertThat(applied).isFalse();
      assertThat(rejectedAtOf(id)).isNull();
    }

    // 입금 대기를 벗어난 참여는 반려 시각을 응답에 노출하지 않는다 — 초기화 CAS 가 재마킹 하나뿐이라
    // 반려 후 개최자가 그냥 입금확인해 주면 CONFIRMED + 반려시각 조합이 남는다 (PR #123 리뷰 4번).
    @Test
    void 입금_대기를_벗어나면_반려_시각을_노출하지_않는다() {
      Long id = insertSentParticipation();
      participationRepository.revertPaymentSentIfSent(
          id, Instant.now().plus(24, ChronoUnit.HOURS), Instant.now(), Instant.now());
      em.clear();

      Participation awaiting = participationRepository.findById(id).orElseThrow();
      assertThat(awaiting.getVisiblePaymentRejectedAt()).isNotNull();

      participationRepository.confirmPaymentIfPayable(id, Instant.now());
      em.clear();

      Participation confirmed = participationRepository.findById(id).orElseThrow();
      assertThat(confirmed.getPaymentRejectedAt()).isNotNull();
      assertThat(confirmed.getVisiblePaymentRejectedAt()).isNull();
    }
  }

  /**
   * 자발 취소 가드(docs/56 H-09)가 기대는 판정 — {@code participations.created_at}(DB 시계)과 {@code
   * buncheols.finalized_at}(성사 확정 CAS 가 앱 시계로 기록)의 선후 — 을 <b>두 값이 실제로 DB 에 쓰이고 다시 읽히는 경로로</b>
   * 검증한다. 도메인 술어 테스트({@code BuncheolTest})와 서비스 테스트는 각각 판정식과 배선만 보므로, 두 컬럼이 만나는 조합은 여기서만
   * 실행된다.
   *
   * <p>참여 생성은 프로덕션 경로({@code saveIfRecruiting}/{@code saveIfCollecting})가 아니라 이 클래스의 raw INSERT
   * 헬퍼를 쓴다 — 조건부 INSERT 는 {@code UTC_TIMESTAMP()} 를 쓰는데 H2 에 없는 함수라 테스트 DB 에서 실행되지 않는다. 헬퍼도
   * {@code created_at} 을 명시하지 않아 스키마 기본값({@code CURRENT_TIMESTAMP})으로 채워지므로 <b>DB 시계</b>라는 전제는
   * 같다. 시각 차는 분 단위로 벌려 DATETIME 초 반올림에 흔들리지 않게 한다.
   */
  @Nested
  @DisplayName("성사 확정 시각과 참여 생성 시각의 선후 (docs/56 H-09)")
  class FinalizeOrderTest {

    private Long createC2cBuncheol() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
      Buncheol buncheol =
          Buncheol.create(
              hostId,
              new BuncheolParams(
                  groupId, "C2C 제목", null, "스토어명", deadline, 1, 3000, null, FlowType.C2C, null),
              Instant.now());
      buncheolRepository.save(buncheol);
      em.flush();
      em.clear();
      return buncheol.getId();
    }

    private void startCollecting(final Long buncheolId, final Instant at) {
      int affected =
          buncheolRepository.startCollectingIfRecruiting(
              buncheolId, at.plus(24, ChronoUnit.HOURS), "국민", "12345678", "개최자", at);
      assertThat(affected).isOne();
      em.clear();
    }

    private Long insertAwaitingParticipation(final Long buncheolId, final Long memberId) {
      return insertParticipation(
          buncheolId,
          memberId,
          participantId,
          insertShippingAddress(participantId, "GS25 확정순서점"),
          30_000L,
          Instant.now().plus(24, ChronoUnit.HOURS),
          ParticipationStatus.AWAITING_PAYMENT,
          null);
    }

    // 추가 모집(docs/46 §4.7-E1) — 성사 확정이 이미 끝난 뒤 생성되므로 "확정을 거친 참여" 로 판정되면 안 된다.
    // 여기서 true 가 나오면 이 경로의 참여자가 신청 즉시 취소 불가로 잠긴다.
    @Test
    void 성사_확정_이후에_생성된_참여는_확정_이전_참여가_아니다() {
      Long buncheolId = createC2cBuncheol();
      Long memberId = createBuncheolMember(buncheolId);
      startCollecting(buncheolId, Instant.now().minus(5, ChronoUnit.MINUTES));

      Long participationId = insertAwaitingParticipation(buncheolId, memberId);
      em.clear();

      Buncheol buncheol = buncheolRepository.findById(buncheolId).orElseThrow();
      Participation saved = participationRepository.findById(participationId).orElseThrow();
      assertThat(buncheol.isCreatedBeforeFinalize(saved.getCreatedAt())).isFalse();
    }

    // 모집중 신청(APPLIED) → 성사 확정 일괄 전이 경로. 확정보다 먼저 만들어졌으므로 취소가 막혀야 한다.
    @Test
    void 성사_확정_이전에_생성된_참여는_확정_이전_참여로_판정된다() {
      Long buncheolId = createC2cBuncheol();
      Long memberId = createBuncheolMember(buncheolId);

      Long participationId = insertAwaitingParticipation(buncheolId, memberId);
      startCollecting(buncheolId, Instant.now().plus(5, ChronoUnit.MINUTES));

      Buncheol buncheol = buncheolRepository.findById(buncheolId).orElseThrow();
      Participation saved = participationRepository.findById(participationId).orElseThrow();
      assertThat(buncheol.isCreatedBeforeFinalize(saved.getCreatedAt())).isTrue();
    }
  }
}
