package buncheoleasy.admin.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.admin.domain.payment.AdminPaymentQueryRepository;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentSummary;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.query.LikeEscaper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
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
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(groupId, title, null, "스토어", deadline, 1, 3000, null),
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
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, amount,"
            + " shipping_fee, refund_bank, refund_account, refund_holder, due_at, confirmed_at,"
            + " cancelled_at, cancel_reason, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
    em.clear();
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM participations", Long.class);
  }

  private void insertDelivery(final Long participationId, final String trackingNumber) {
    jdbcTemplate.update(
        "INSERT INTO deliveries (participation_id, shipping_method, store_name,"
            + " receiver_nickname, receiver_phone_number, tracking_number, status)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        participationId,
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
      assertThat(view.participation().getTotalAmount()).isEqualTo(13000);
      assertThat(view.delivery()).isNull();
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
