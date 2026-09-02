package buncheoleasy.admin.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.admin.domain.payment.AdminPaymentQueryRepository;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentSummary;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.query.LikeEscaper;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Map;
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
@DisplayName("JpaAdminPaymentQueryRepositoryAdapter 테스트")
class JpaAdminPaymentQueryRepositoryAdapterTest {

  private static final Instant BASE_TIME = Instant.parse("2026-07-01T00:00:00Z");

  @Autowired private AdminPaymentQueryRepository adminPaymentQueryRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long participantId;
  // 분철당 활성 참여는 참여자별 1건(uq_participations_active_participant)이라, 같은 분철에
  // 활성 참여를 여러 건 깔아야 하는 픽스처는 유저를 나눠 쓴다.
  private Long secondParticipantId;
  private Long thirdParticipantId;
  private Long groupId;
  private Long groupMemberId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "adminhost");
    participantId = TestUserFixture.insertUser(jdbcTemplate, "adminbuyer");
    secondParticipantId = TestUserFixture.insertUser(jdbcTemplate, "adminbuyerB");
    thirdParticipantId = TestUserFixture.insertUser(jdbcTemplate, "adminbuyerC");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "관리자 테스트 그룹");
    groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "관리자 테스트 멤버");
  }

  private Long insertBuncheol(final String title) {
    return insertBuncheol(title, FlowType.LEGACY);
  }

  /**
   * ⚠️ 다슬롯 묶음 픽스처는 <b>반드시 C2C</b> 여야 한다 — LEGACY 는 {@code
   * uq_participations_legacy_active_participant} 가 1인 1활성슬롯을 강제해 같은 참여자의 두 번째 INSERT 가
   * 그 자리에서 막힌다. 그것이 정상 동작이고, 그래서 혼재 묶음도 C2C 에만 존재한다.
   */
  private Long insertBuncheol(final String title, final FlowType flowType) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, title, null, "스토어", deadline, 1, 3000, null, flowType, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    return buncheol.getId();
  }

  private Long insertSlot(final Long buncheolId, final Long memberId) {
    jdbcTemplate.update(
        "INSERT INTO buncheol_members (buncheol_id, member_id, price) VALUES (?, ?, ?)",
        buncheolId,
        memberId,
        10000L);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM buncheol_members", Long.class);
  }

  private Long insertParticipation(
      final Long buncheolId,
      final Long slotId,
      final Long participantId,
      final long amount,
      final long shippingFee,
      final String status,
      final Instant confirmedAt,
      final Instant createdAt) {
    // 배송 조인 키가 묶음이다 (택배 1개 = 묶음 1개) — 참여마다 묶음을 하나 만들어 붙인다.
    // 배송비의 정본은 묶음이다 — 픽스처도 그렇게 심어야 합계가 실제와 같아진다.
    Long bundleId = insertBundle(buncheolId, participantId, shippingFee);
    jdbcTemplate.update(
        // ⚠️ participations.flow_type 은 분철에서 복사하는 <b>비정규화 컬럼</b>이다(generated column 은 타
        // 테이블을 못 본다). 안 실으면 DEFAULT 'LEGACY' 가 걸려 1인 1활성슬롯 유니크에 막힌다 —
        // 다슬롯 픽스처가 그 자리에서 죽는다.
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, amount,"
            + " shipping_fee, refund_bank, refund_account, refund_holder, due_at, confirmed_at,"
            + " cancelled_at, cancel_reason, status, bundle_id, flow_type, created_at, updated_at)"
            + " SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, b.flow_type, ?, ?"
            + " FROM buncheols b WHERE b.id = ?",
        buncheolId,
        slotId,
        participantId,
        amount,
        shippingFee,
        "국민",
        "12345678",
        "홍길동",
        Timestamp.from(BASE_TIME.plus(30, ChronoUnit.MINUTES)),
        confirmedAt == null ? null : Timestamp.from(confirmedAt),
        "CANCELLED".equals(status) ? Timestamp.from(BASE_TIME) : null,
        "CANCELLED".equals(status)
            ? (confirmedAt != null ? "BUNCHEOL_CANCELLED" : "PAYMENT_TIMEOUT")
            : null,
        status,
        bundleId,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        buncheolId);
    em.clear();
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM participations", Long.class);
  }

  private Long insertBundle(
      final Long buncheolId, final Long participantId, final long shippingFee) {
    jdbcTemplate.update(
        "INSERT INTO participation_bundles (buncheol_id, participant_id, shipping_fee,"
            + " refund_bank, refund_account, refund_holder) VALUES (?, ?, ?, ?, ?, ?)",
        buncheolId,
        participantId,
        shippingFee,
        "국민",
        "12345678",
        "홍길동");
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM participation_bundles", Long.class);
  }

  /** 두 참여를 같은 묶음으로 합친다 — 다슬롯 묶음(자리 여러 개, 이체 1회)의 모양. */
  private void shareBundle(final Long keeperId, final Long joinerId) {
    Long bundleId =
        jdbcTemplate.queryForObject(
            "SELECT bundle_id FROM participations WHERE id = ?", Long.class, keeperId);
    jdbcTemplate.update(
        "UPDATE participations SET bundle_id = ? WHERE id = ?", bundleId, joinerId);
  }

  private void insertDelivery(final Long participationId, final String trackingNumber) {
    Long bundleId =
        jdbcTemplate.queryForObject(
            "SELECT bundle_id FROM participations WHERE id = ?", Long.class, participationId);
    jdbcTemplate.update(
        "INSERT INTO deliveries (participation_id, bundle_id, shipping_method, store_name,"
            + " receiver_nickname, receiver_phone_number, tracking_number, status)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        participationId,
        bundleId,
        "GS25_HALF",
        "테스트지점",
        "수령닉네임",
        "01012345678",
        trackingNumber,
        trackingNumber == null ? "SNAPSHOTTED" : "SHIPPING");
    em.clear();
  }

  private List<AdminPaymentView> findAll() {
    return adminPaymentQueryRepository.findPayments(null, null, Cursor.firstPage(), 100);
  }

  @Nested
  @DisplayName("findPayments 목록/조인 테스트")
  class FindPaymentsTest {

    @Test
    void 참여를_축으로_분철_그룹_참여자_멤버를_조인해_최신_참여순으로_반환한다() {
      // given
      Long buncheolId = insertBuncheol("포카 분철");
      Long slot1 = insertSlot(buncheolId, groupMemberId);
      Long slot2 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "멤버2"));
      Long older =
          insertParticipation(
              buncheolId, slot1, participantId, 10000, 3000, "AWAITING_PAYMENT", null, BASE_TIME);
      Long newer =
          insertParticipation(
              buncheolId,
              slot2,
              secondParticipantId,
              20000,
              0,
              "AWAITING_PAYMENT",
              null,
              BASE_TIME.plusSeconds(60));

      // when
      List<AdminPaymentView> views = findAll();

      // then
      assertThat(views).hasSize(2);
      assertThat(views.get(0).participation().getId()).isEqualTo(newer);
      assertThat(views.get(1).participation().getId()).isEqualTo(older);

      AdminPaymentView view = views.get(1);
      assertThat(view.buncheol().getTitle()).isEqualTo("포카 분철");
      assertThat(view.group().getName()).isEqualTo("관리자 테스트 그룹");
      assertThat(view.member().getName()).isEqualTo("관리자 테스트 멤버");
      assertThat(view.participant().getNickname().value()).isEqualTo("Guestadminbuyer");
      // 금액은 이제 뷰가 아니라 귀속 판정이 낸다(AdminPaymentQueryService 와 같은 계산).
      assertThat(view.participation().getAmount() + view.participation().getShippingFee())
          .isEqualTo(13000);
      assertThat(view.delivery()).isNull();
      // 픽스처가 배송지를 지정하지 않았으므로 LEFT JOIN 으로 행이 보존되고 배송지는 null 이다.
      assertThat(view.shippingAddress()).isNull();
    }

    @Test
    void 참여가_선택한_배송지를_함께_반환한다() {
      // 입금확인 전(배송 스냅샷 없음)에도 운영자가 "결제 요청 배송지" 를 확인할 수 있어야 한다.
      Long buncheolId = insertBuncheol("결제요청배송지 분철");
      Long slotId = insertSlot(buncheolId, groupMemberId);
      jdbcTemplate.update(
          "INSERT INTO shipping_addresses (user_id, shipping_method, store_name) VALUES (?, ?, ?)",
          participantId,
          "GS25_HALF",
          "결제요청지점");
      Long addressId =
          jdbcTemplate.queryForObject("SELECT MAX(id) FROM shipping_addresses", Long.class);
      Long participationId =
          insertParticipation(
              buncheolId, slotId, participantId, 10000, 0, "AWAITING_PAYMENT", null, BASE_TIME);
      jdbcTemplate.update(
          "UPDATE participations SET shipping_address_id = ? WHERE id = ?",
          addressId,
          participationId);
      em.clear();

      AdminPaymentView view = findAll().getFirst();

      assertThat(view.delivery()).isNull();
      assertThat(view.shippingAddress()).isNotNull();
      assertThat(view.shippingAddress().getStoreName()).isEqualTo("결제요청지점");
      assertThat(view.shippingAddress().getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
    }

    @Test
    void 배송_스냅샷이_있으면_함께_반환한다() {
      // given
      Long buncheolId = insertBuncheol("배송 분철");
      Long slotId = insertSlot(buncheolId, groupMemberId);
      Long participationId =
          insertParticipation(
              buncheolId, slotId, participantId, 10000, 0, "CONFIRMED", BASE_TIME, BASE_TIME);
      insertDelivery(participationId, "TRACK-1234");

      // when
      List<AdminPaymentView> views = findAll();

      // then
      assertThat(views).hasSize(1);
      assertThat(views.getFirst().delivery()).isNotNull();
      assertThat(views.getFirst().delivery().getTrackingNumber()).isEqualTo("TRACK-1234");
    }

    // 🔴 회귀 방지 — 배송은 묶음에 붙어 있어 같은 묶음의 미입금 슬롯도 조인 키가 맞는다. 게이트가
    // 없으면 운영자가 <b>입금하지도 않은 슬롯을 운송장과 함께</b> 보게 되고, 이 화면이 입금 확인
    // 판단의 근거다. 혼재 묶음은 도달 가능하다 — 슬롯 단위 확인과 어드민 벌크 확인이 열려 있다.
    @Test
    void 같은_묶음의_미입금_슬롯에는_배송이_붙지_않는다() {
      Long buncheolId = insertBuncheol("혼재 묶음 분철", FlowType.C2C);
      Long confirmedSlot = insertSlot(buncheolId, groupMemberId);
      Long pendingSlot =
          insertSlot(
              buncheolId,
              TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "혼재 묶음 멤버"));
      Long confirmedId =
          insertParticipation(
              buncheolId, confirmedSlot, participantId, 10000, 3000, "CONFIRMED", BASE_TIME,
              BASE_TIME);
      Long pendingId =
          insertParticipation(
              buncheolId, pendingSlot, participantId, 10000, 0, "AWAITING_PAYMENT", null,
              BASE_TIME);
      // 같은 사람이 자리 2개를 잡은 모양 — 묶음이 하나다.
      shareBundle(confirmedId, pendingId);
      insertDelivery(confirmedId, "TRACK-1234");

      List<AdminPaymentView> views = findAll();

      Map<Long, AdminPaymentView> byId =
          views.stream()
              .collect(
                  Collectors.toMap(v -> v.participation().getId(), Function.identity()));
      assertThat(byId.get(confirmedId).delivery()).isNotNull();
      // 이 한 줄이 이번 변경의 전부다.
      assertThat(byId.get(pendingId).delivery()).isNull();
      // LEFT JOIN 이라 행은 보존된다 — 조건을 WHERE 로 옮기면 미입금 행이 통째로 사라진다.
      assertThat(views).hasSize(2);
    }

    @Test
    void 탈퇴한_참여자의_행은_유지되고_참여자만_null_이_된다() {
      // given
      Long buncheolId = insertBuncheol("탈퇴 분철");
      Long slotId = insertSlot(buncheolId, groupMemberId);
      insertParticipation(
          buncheolId, slotId, participantId, 10000, 0, "AWAITING_PAYMENT", null, BASE_TIME);
      jdbcTemplate.update(
          "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", participantId);
      em.clear();

      // when
      List<AdminPaymentView> views = findAll();

      // then
      assertThat(views).hasSize(1);
      assertThat(views.getFirst().participant()).isNull();
    }
  }

  @Nested
  @DisplayName("findPayments 파생 상태 필터 테스트")
  class StatusFilterTest {

    private Long awaitingId;
    private Long confirmedId;
    private Long refundRequiredId;
    private Long cancelledId;

    @BeforeEach
    void insertAllStatuses() {
      Long buncheolId = insertBuncheol("상태 분철");
      Long slot1 = insertSlot(buncheolId, groupMemberId);
      Long slot2 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m2"));
      Long slot3 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m3"));
      Long slot4 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m4"));
      awaitingId =
          insertParticipation(
              buncheolId, slot1, participantId, 10000, 0, "AWAITING_PAYMENT", null, BASE_TIME);
      confirmedId =
          insertParticipation(
              buncheolId, slot2, secondParticipantId, 10000, 0, "CONFIRMED", BASE_TIME, BASE_TIME);
      // 입금확인 후 분철 취소 cascade → 환불 필요
      refundRequiredId =
          insertParticipation(
              buncheolId, slot3, participantId, 10000, 0, "CANCELLED", BASE_TIME, BASE_TIME);
      // 입금 기한 초과 취소 → 환불 불필요
      cancelledId =
          insertParticipation(
              buncheolId, slot4, participantId, 10000, 0, "CANCELLED", null, BASE_TIME);
    }

    @Test
    void 상태_필터가_없으면_전체를_반환한다() {
      assertThat(findAll()).hasSize(4);
    }

    @Test
    void 입금확인_대기_필터() {
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(
              AdminPaymentStatus.AWAITING_CONFIRMATION, null, Cursor.firstPage(), 100);
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(awaitingId);
    }

    @Test
    void 입금확인_완료_필터() {
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(
              AdminPaymentStatus.CONFIRMED, null, Cursor.firstPage(), 100);
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(confirmedId);
    }

    @Test
    void 환불_필요_필터는_입금확인_이력이_있는_취소_건만_반환한다() {
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(
              AdminPaymentStatus.REFUND_REQUIRED, null, Cursor.firstPage(), 100);
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(refundRequiredId);
    }

    @Test
    void 취소_필터는_입금확인_이력이_없는_취소_건만_반환한다() {
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(
              AdminPaymentStatus.CANCELLED, null, Cursor.firstPage(), 100);
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(cancelledId);
    }
  }

  @Nested
  @DisplayName("findPayments 키워드 필터 테스트")
  class KeywordFilterTest {

    @Test
    void 분철_제목으로_검색한다() {
      // given
      Long target = insertBuncheol("아이브 포카 분철");
      Long other = insertBuncheol("다른 분철");
      Long targetParticipation =
          insertParticipation(
              target,
              insertSlot(target, groupMemberId),
              participantId,
              10000,
              0,
              "AWAITING_PAYMENT",
              null,
              BASE_TIME);
      insertParticipation(
          other,
          insertSlot(other, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m2")),
          participantId,
          10000,
          0,
          "AWAITING_PAYMENT",
          null,
          BASE_TIME);

      // when
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(null, "아이브 포카", Cursor.firstPage(), 100);

      // then
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(targetParticipation);
    }

    @Test
    void LIKE_와일드카드가_포함된_검색어는_리터럴로_매칭된다() {
      // given — "%"가 와일드카드로 해석되면 두 분철 모두 매칭돼 버린다
      Long target = insertBuncheol("100% 정품 분철");
      Long other = insertBuncheol("100 정품 분철");
      Long targetParticipation =
          insertParticipation(
              target,
              insertSlot(target, groupMemberId),
              participantId,
              10000,
              0,
              "AWAITING_PAYMENT",
              null,
              BASE_TIME);
      insertParticipation(
          other,
          insertSlot(other, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m9")),
          participantId,
          10000,
          0,
          "AWAITING_PAYMENT",
          null,
          BASE_TIME);

      // when — 호출 측(서비스)과 동일하게 이스케이프된 키워드를 넘긴다
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(
              null, LikeEscaper.escape("100%"), Cursor.firstPage(), 100);

      // then
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(targetParticipation);
    }

    @Test
    void 참여자_닉네임으로_검색한다() {
      // given
      Long buncheolId = insertBuncheol("닉네임 분철");
      Long slot1 = insertSlot(buncheolId, groupMemberId);
      Long slot2 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "m2"));
      Long otherParticipantId = TestUserFixture.insertUser(jdbcTemplate, "someoneelse");
      Long targetParticipation =
          insertParticipation(
              buncheolId, slot1, participantId, 10000, 0, "AWAITING_PAYMENT", null, BASE_TIME);
      insertParticipation(
          buncheolId, slot2, otherParticipantId, 10000, 0, "AWAITING_PAYMENT", null, BASE_TIME);

      // when — 참여자 닉네임은 Guestadminbuyer (TestUserFixture 규칙)
      List<AdminPaymentView> views =
          adminPaymentQueryRepository.findPayments(null, "adminbuyer", Cursor.firstPage(), 100);

      // then
      assertThat(views)
          .extracting(view -> view.participation().getId())
          .containsExactly(targetParticipation);
    }
  }

  @Nested
  @DisplayName("findPayments 커서 페이지네이션 테스트")
  class CursorTest {

    @Test
    void 커서_이후의_행만_size만큼_반환한다() {
      // given
      Long buncheolId = insertBuncheol("커서 분철");
      Long first = null;
      Long second = null;
      Long third = null;
      for (int i = 0; i < 3; i++) {
        Long slotId =
            insertSlot(
                buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "cm" + i));
        Long cursorBuyerId = TestUserFixture.insertUser(jdbcTemplate, "cursorbuyer" + i);
        Long id =
            insertParticipation(
                buncheolId,
                slotId,
                cursorBuyerId,
                10000,
                0,
                "AWAITING_PAYMENT",
                null,
                BASE_TIME.plusSeconds(i * 60L));
        if (i == 0) first = id;
        if (i == 1) second = id;
        if (i == 2) third = id;
      }

      // when — 첫 페이지 size 2 (limit 은 size + 1 로 hasNext 판별)
      List<AdminPaymentView> firstPage =
          adminPaymentQueryRepository.findPayments(null, null, Cursor.firstPage(), 3);
      assertThat(firstPage)
          .extracting(view -> view.participation().getId())
          .containsExactly(third, second, first);

      // 두 번째(second)까지 본 커서로 다음 페이지 조회
      Cursor cursor =
          new Cursor(firstPage.get(1).participation().getCreatedAt(), second);
      List<AdminPaymentView> nextPage =
          adminPaymentQueryRepository.findPayments(null, null, cursor, 3);

      // then
      assertThat(nextPage).extracting(view -> view.participation().getId()).containsExactly(first);
    }
  }

  @Nested
  @DisplayName("countConfirmedByBuncheolIds 테스트")
  class CountConfirmedTest {

    @Test
    void 분철별_입금확인_참여_수를_집계한다() {
      // given
      Long buncheolA = insertBuncheol("분철A");
      Long buncheolB = insertBuncheol("분철B");
      insertParticipation(
          buncheolA,
          insertSlot(buncheolA, groupMemberId),
          participantId,
          10000,
          0,
          "CONFIRMED",
          BASE_TIME,
          BASE_TIME);
      insertParticipation(
          buncheolA,
          insertSlot(buncheolA, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "a2")),
          secondParticipantId,
          10000,
          0,
          "CONFIRMED",
          BASE_TIME,
          BASE_TIME);
      insertParticipation(
          buncheolB,
          insertSlot(buncheolB, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "b1")),
          participantId,
          10000,
          0,
          "AWAITING_PAYMENT",
          null,
          BASE_TIME);

      // when
      List<BuncheolConfirmedCount> counts =
          adminPaymentQueryRepository.countConfirmedByBuncheolIds(List.of(buncheolA, buncheolB));

      // then — 확정이 없는 분철은 행 자체가 없다 (호출 측이 0 으로 보정)
      assertThat(counts).containsExactly(new BuncheolConfirmedCount(buncheolA, 2));
    }

    @Test
    void 빈_id_목록이면_빈_결과를_반환한다() {
      assertThat(adminPaymentQueryRepository.countConfirmedByBuncheolIds(List.of())).isEmpty();
    }
  }

  @Nested
  @DisplayName("summarize 테스트")
  class SummarizeTest {

    @Test
    void 파생_상태별_건수와_확인_대기_금액을_집계한다() {
      // given
      Long buncheolId = insertBuncheol("통계 분철");
      Long slot1 = insertSlot(buncheolId, groupMemberId);
      Long slot2 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "s2"));
      Long slot3 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "s3"));
      Long slot4 =
          insertSlot(buncheolId, TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "s4"));
      insertParticipation(
          buncheolId, slot1, participantId, 10000, 3000, "AWAITING_PAYMENT", null, BASE_TIME);
      insertParticipation(
          buncheolId, slot2, secondParticipantId, 20000, 0, "AWAITING_PAYMENT", null, BASE_TIME);
      insertParticipation(
          buncheolId, slot3, thirdParticipantId, 30000, 0, "CONFIRMED", BASE_TIME, BASE_TIME);
      insertParticipation(
          buncheolId, slot4, participantId, 40000, 0, "CANCELLED", BASE_TIME, BASE_TIME);

      // when
      AdminPaymentSummary summary = adminPaymentQueryRepository.summarize();

      // then
      assertThat(summary.awaitingCount()).isEqualTo(2);
      assertThat(summary.confirmedCount()).isEqualTo(1);
      assertThat(summary.refundRequiredCount()).isEqualTo(1);
      assertThat(summary.cancelledCount()).isEqualTo(0);
      assertThat(summary.totalCount()).isEqualTo(4);
      assertThat(summary.awaitingAmount()).isEqualTo(33000);
    }

    @Test
    void 결제가_없으면_모두_0_이다() {
      AdminPaymentSummary summary = adminPaymentQueryRepository.summarize();
      assertThat(summary)
          .isEqualTo(new AdminPaymentSummary(0, 0, 0, 0, 0, 0));
    }
  }
}
