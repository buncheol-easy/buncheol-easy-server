package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
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

  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");

  @Autowired private ParticipationRepository participationRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long participantId;
  private Long groupId;
  private Long groupMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_xx");
    participantId = TestUserFixture.insertUser(jdbcTemplate, "participant_xx");
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
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, minHeadcount, 3000, null),
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
    insertParticipation(
        buncheolId,
        slotId,
        participantId,
        insertShippingAddress(participantId, storeName),
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
  @DisplayName("existsActiveByParticipantId — 탈퇴 가드용 활성 참여 존재 여부")
  class ExistsActiveByParticipantIdTest {

    @Test
    void 활성_참여가_있으면_true_를_반환한다() {
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

      assertThat(participationRepository.existsActiveByParticipantId(participantId)).isTrue();
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

      assertThat(participationRepository.existsActiveByParticipantId(participantId)).isFalse();
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
  @DisplayName("findConfirmedByBuncheolId / countConfirmedByBuncheolId — 입금확인 참여만")
  class ConfirmedByBuncheolIdTest {

    @Test
    void CONFIRMED_참여만_조회하고_AWAITING_PAYMENT_은_제외한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "확정멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrConfirmed = insertShippingAddress(participantId, "확정매장");
      Long addrAwaiting = insertShippingAddress(participantId, "대기매장");

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
          participantId,
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
  @DisplayName("findOverduePaymentTargets — 입금 만료 스케줄러 폴링")
  class FindOverduePaymentTargetsTest {

    @Test
    void AWAITING_PAYMENT_이고_기한이_지난_건만_조회한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "만료멤버");
      Long bmId2 = createBuncheolMember(buncheolId, otherMemberId);
      Long addrOverdue = insertShippingAddress(participantId, "만료매장");
      Long addrFuture = insertShippingAddress(participantId, "미래매장");
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
          participantId,
          addrFuture,
          30_000L,
          now.plus(20, ChronoUnit.MINUTES),
          ParticipationStatus.AWAITING_PAYMENT,
          null);

      List<Participation> result = participationRepository.findOverduePaymentTargets(now, 100);

      assertThat(result).extracting(Participation::getId).containsExactly(overdueId);
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
          participantId,
          insertShippingAddress(participantId, "리밋매장2"),
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
      Long addrConfirmed = insertShippingAddress(participantId, "확정일괄");
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
              participantId,
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
      insertConfirmedParticipation(buncheolId, slot2, "확정매장2");

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
          participantId,
          insertShippingAddress(participantId, "대기매장2"),
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
      insertConfirmedParticipation(buncheolId, slot2, "확정매장2");

      int updated = buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, 2, Instant.now());

      assertThat(updated).isZero();
      assertThat(buncheolStatusOf(buncheolId)).isEqualTo("RECRUITING");
    }
  }

}
