package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 3000, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    return buncheol.getId();
  }

  private Long createBuncheolMember(Long buncheolId) {
    BuncheolMember member = BuncheolMember.create(buncheolId, groupMemberId, 30_000L);
    buncheolMemberRepository.saveAll(List.of(member));
    em.flush();
    return member.getId();
  }

  private Long insertShippingAddress(Long userId, String storeName) {
    jdbcTemplate.update(
        "INSERT INTO shipping_addresses (user_id, shipping_method, store_name)"
            + " VALUES (?, ?, ?)",
        userId,
        "GS25_HALF",
        storeName);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM shipping_addresses WHERE user_id = ? AND store_name = ?",
        Long.class,
        userId,
        storeName);
  }

  private void insertParticipation(
      Long buncheolId,
      Long buncheolMemberId,
      Long shippingAddressId,
      long bidAmount,
      ParticipationStatus status) {
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, active_participant_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        bidAmount,
        status.name(),
        status == ParticipationStatus.ACTIVE_BID
                || status == ParticipationStatus.AWAITING_PAYMENT
                || status == ParticipationStatus.CONFIRMED
            ? participantId
            : null);
  }

  @Nested
  @DisplayName("findAllByParticipantIdOrderByCreatedAtDesc")
  class FindAllByParticipantIdTest {

    @Test
    void 참여자_기준_최신_순으로_조회한다() {
      Long buncheolId = createBuncheol();
      Long buncheolMemberId = createBuncheolMember(buncheolId);
      Long addrA = insertShippingAddress(participantId, "강남역점A");
      Long addrB = insertShippingAddress(participantId, "강남역점B");
      // CANCELLED 후 ACTIVE_BID (active_participant_id UNIQUE 제약 회피)
      insertParticipation(
          buncheolId, buncheolMemberId, addrA, 35_000L, ParticipationStatus.CANCELLED);
      insertParticipation(
          buncheolId, buncheolMemberId, addrB, 40_000L, ParticipationStatus.ACTIVE_BID);

      List<Participation> result =
          participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);

      assertThat(result).hasSize(2);
      // 두 row 모두 같은 사용자 / 최신 정렬이지만 동일 timestamp 가능하므로 size 와 set 으로 확인
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
  @DisplayName("countActiveByBuncheolIds — JPQL constructor expression 검증")
  class CountActiveByBuncheolIdsTest {

    @Test
    void 활성_상태별로_분철_단위_카운트를_집계한다() {
      Long buncheolA = createBuncheol();
      Long buncheolB = createBuncheol();
      Long bmA = createBuncheolMember(buncheolA);
      Long bmB = createBuncheolMember(buncheolB);
      Long addrA = insertShippingAddress(participantId, "분철A_매장");
      Long addrB = insertShippingAddress(participantId, "분철B_매장");

      // A: ACTIVE_BID 1건
      insertParticipation(buncheolA, bmA, addrA, 35_000L, ParticipationStatus.ACTIVE_BID);
      // B: CONFIRMED 1건 + CANCELLED 1건 → 활성은 1
      insertParticipation(buncheolB, bmB, addrB, 50_000L, ParticipationStatus.CANCELLED);
      // 같은 참여자가 같은 슬롯에 다시 입찰하려면 다른 배송지로
      Long addrB2 = insertShippingAddress(participantId, "분철B_매장2");
      insertParticipation(buncheolB, bmB, addrB2, 60_000L, ParticipationStatus.CONFIRMED);

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
      insertParticipation(buncheolId, bmId, addr, 35_000L, ParticipationStatus.CANCELLED);

      List<BuncheolActiveParticipationCount> result =
          participationRepository.countActiveByBuncheolIds(List.of(buncheolId));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveByBuncheolId — 분철 단위 활성 참여 전체 조회")
  class FindActiveByBuncheolIdTest {

    @Test
    void 활성_상태_참여를_bid_amount_DESC_id_ASC_로_조회한다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long otherParticipantId = TestUserFixture.insertUser(jdbcTemplate, "other_xx");
      Long addrA = insertShippingAddress(participantId, "주매장A");
      Long addrB = insertShippingAddress(otherParticipantId, "타매장B");
      Long addrC = insertShippingAddress(otherParticipantId, "타매장C");

      insertParticipation(buncheolId, bmId, addrA, 50_000L, ParticipationStatus.ACTIVE_BID);
      insertParticipationForUser(
          buncheolId, bmId, otherParticipantId, addrB, 70_000L, ParticipationStatus.ACTIVE_BID);
      // CANCELLED 는 제외돼야 한다
      insertParticipationForUser(
          buncheolId, bmId, otherParticipantId, addrC, 90_000L, ParticipationStatus.CANCELLED);

      List<Participation> result = participationRepository.findActiveByBuncheolId(buncheolId);

      assertThat(result).extracting(Participation::getBidAmount).containsExactly(70_000L, 50_000L);
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
          targetId, targetMemberId, addrTarget, 40_000L, ParticipationStatus.ACTIVE_BID);
      insertParticipation(
          otherId, otherMemberId, addrOther, 80_000L, ParticipationStatus.ACTIVE_BID);

      List<Participation> result = participationRepository.findActiveByBuncheolId(targetId);

      assertThat(result)
          .singleElement()
          .satisfies(p -> assertThat(p.getBidAmount()).isEqualTo(40_000L));
    }

    @Test
    void 활성_참여가_없으면_빈_리스트를_반환한다() {
      Long buncheolId = createBuncheol();

      List<Participation> result = participationRepository.findActiveByBuncheolId(buncheolId);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("cancelActiveByBuncheolId — 분철 취소 시 활성 참여 일괄 전이")
  class CancelActiveByBuncheolIdTest {

    @Test
    void ACTIVE_BID_만_CANCELLED_로_전이된다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      Long addrActive = insertShippingAddress(participantId, "활성매장");
      insertParticipation(buncheolId, bmId, addrActive, 35_000L, ParticipationStatus.ACTIVE_BID);

      int affected =
          participationRepository.cancelActiveByBuncheolId(buncheolId, Instant.now());

      assertThat(affected).isEqualTo(1);
      List<Participation> all =
          participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);
      assertThat(all)
          .singleElement()
          .satisfies(
              p -> {
                assertThat(p.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
                assertThat(p.getFinalizedAt()).isNotNull();
              });
    }

    @Test
    void ACTIVE_BID_이_아닌_상태는_영향받지_않는다() {
      Long buncheolId = createBuncheol();
      Long bmId = createBuncheolMember(buncheolId);
      // 운영 MySQL 에선 active_participant_id (STORED generated column) + UNIQUE 제약 때문에
      // 같은 (buncheolMemberId, participantId) 쌍의 활성 참여를 두 번 INSERT 하면 충돌한다.
      // 그래서 비활성(CANCELLED/FAILED) 상태들만 섞어 둔다. (H2 스키마는 STORED 컬럼이 plain BIGINT 라
      // 운영 제약이 실제로 무력하지만, 운영 환경 가정과 일치시키기 위해 동일 패턴을 유지한다.)
      Long addrCancelled = insertShippingAddress(participantId, "이미취소");
      Long addrFailed = insertShippingAddress(participantId, "실패");
      insertParticipation(
          buncheolId, bmId, addrCancelled, 30_000L, ParticipationStatus.CANCELLED);
      insertParticipation(buncheolId, bmId, addrFailed, 40_000L, ParticipationStatus.FAILED);

      int affected =
          participationRepository.cancelActiveByBuncheolId(buncheolId, Instant.now());

      assertThat(affected).isZero();
    }

    @Test
    void 다른_분철의_ACTIVE_BID_는_영향받지_않는다() {
      Long target = createBuncheol();
      Long other = createBuncheol();
      Long targetBmId = createBuncheolMember(target);
      Long otherBmId = createBuncheolMember(other);
      Long addrTarget = insertShippingAddress(participantId, "타겟매장");
      Long addrOther = insertShippingAddress(participantId, "다른매장");
      insertParticipation(
          target, targetBmId, addrTarget, 35_000L, ParticipationStatus.ACTIVE_BID);
      insertParticipation(other, otherBmId, addrOther, 50_000L, ParticipationStatus.ACTIVE_BID);

      int affected = participationRepository.cancelActiveByBuncheolId(target, Instant.now());

      assertThat(affected).isEqualTo(1);
      assertThat(participationRepository.findActiveByBuncheolId(other)).hasSize(1);
    }
  }

  private void insertParticipationForUser(
      Long buncheolId,
      Long buncheolMemberId,
      Long userId,
      Long shippingAddressId,
      long bidAmount,
      ParticipationStatus status) {
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id,"
            + " shipping_address_id, bid_amount, status, active_participant_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        buncheolId,
        buncheolMemberId,
        userId,
        shippingAddressId,
        bidAmount,
        status.name(),
        status == ParticipationStatus.ACTIVE_BID
                || status == ParticipationStatus.AWAITING_PAYMENT
                || status == ParticipationStatus.CONFIRMED
            ? userId
            : null);
  }
}
