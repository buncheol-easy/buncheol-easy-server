package buncheoleasy.admin.application;

import org.junit.jupiter.api.BeforeEach;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.admin.domain.payment.AdminPaymentQueryRepository;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentSummary;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.admin.dto.response.AdminPaymentRecordResponse;
import buncheoleasy.admin.dto.response.AdminPaymentSummaryResponse;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.group.domain.Group;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPaymentQueryService 단위 테스트")
class AdminPaymentQueryServiceTest {

  private static final Instant BASE_TIME = Instant.parse("2026-07-01T00:00:00Z");
  private static final Instant BUNDLE_DUE_AT = Instant.parse("2026-07-08T00:00:00Z");

  @InjectMocks private AdminPaymentQueryService adminPaymentQueryService;

  @Mock private AdminPaymentQueryRepository adminPaymentQueryRepository;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  /** C2C 뷰 — 자리에는 「옛 기한」을, 묶음에는 정본을 심어 어느 쪽을 읽는지 드러나게 한다. */
  private AdminPaymentView c2cView(
      final long participationId, final long buncheolId, final Long bundleId, final Instant 자리기한) {
    AdminPaymentView base = view(participationId, buncheolId, BASE_TIME);
    ReflectionTestUtils.setField(base.participation(), "bundleId", bundleId);
    ReflectionTestUtils.setField(base.participation(), "dueAt", 자리기한);
    ReflectionTestUtils.setField(base.buncheol(), "flowType", FlowType.C2C);
    return base;
  }

  private AdminPaymentView view(
      final long participationId, final long buncheolId, final Instant createdAt) {
    Participation participation =
        Participation.create(
            buncheolId,
            1L,
            2L,
            3L,
            10000L,
            0L,
            BASE_TIME.plus(30, ChronoUnit.MINUTES));
    ReflectionTestUtils.setField(participation, "id", participationId);
    ReflectionTestUtils.setField(participation, "createdAt", createdAt);

    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            9L,
            new BuncheolParams(1L, "분철 제목", null, "스토어", deadline, 2, 3000, null, FlowType.LEGACY, null),
            Instant.now());
    ReflectionTestUtils.setField(buncheol, "id", buncheolId);

    return new AdminPaymentView(
        participation, buncheol, new Group(1L, "그룹", null), null, null, null, null);
  }


  /** 계좌의 정본은 묶음이다 (P2-c) — bundleId 가 있는 참여에는 계좌 있는 묶음을 돌려준다. */
  @BeforeEach
  void stubBundles() {
    // 배송비 귀속. 이 목록은 커서 페이지네이션이라 서비스가 형제 슬롯을 대신 읽어 주는 진입점을 쓴다.
    // 여기 픽스처는 묶음당 슬롯이 하나뿐이라 판정 결과가 저장값과 같다 — 판정 자체의 검증은
    // ShippingFeeAttributionTest · ParticipationBundleDomainServiceTest 가 한다.
    lenient()
        .when(participationBundleDomainService.shippingFeeAttributionFor(anyCollection(), any()))
        .thenReturn(ShippingFeeAttribution.empty());
    lenient()
        .when(participationBundleDomainService.findAllByParticipations(any()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<Participation> participations = invocation.getArgument(0);
              java.util.Map<Long, ParticipationBundle> byId = new java.util.HashMap<>();
              for (Participation participation : participations) {
                if (participation.getBundleId() == null) {
                  continue;
                }
                ParticipationBundle bundle = mock(ParticipationBundle.class);
                lenient()
                    .when(bundle.getRefundAccount())
                    .thenReturn(RefundAccount.of("국민", "12345678", "홍길동"));
                // 🔴 자리 값과 「다른」 기한을 심는다 — 같게 두면 이관 회귀를 못 잡는다.
                lenient().when(bundle.getDueAt()).thenReturn(BUNDLE_DUE_AT);
                byId.put(participation.getBundleId(), bundle);
              }
              return byId;
            });
  }

  @Nested
  @DisplayName("getPayments 테스트")
  class GetPaymentsTest {

    // 🔴 운영자가 통장 대사에 쓰는 화면이라 개최 관리와 갈리면 안 된다 — totalAmount 와 같은 이유다.
    // 부분 반려가 슬롯 하나만 밀고 묶음 전체를 연장하므로 형제 슬롯에서 실제로 두 값이 갈린다.
    @Test
    void C2C_결제목록의_기한은_묶음_정본을_읽는다() {
      Instant 자리기한 = BASE_TIME.plus(1, ChronoUnit.DAYS);
      given(adminPaymentQueryRepository.findPayments(isNull(), isNull(), any(Cursor.class), eq(2)))
          .willReturn(List.of(c2cView(1L, 10L, 900L, 자리기한)));
      given(adminPaymentQueryRepository.countConfirmedByBuncheolIds(List.of(10L)))
          .willReturn(List.of(new BuncheolConfirmedCount(10L, 1)));

      CursorResponse<AdminPaymentRecordResponse> response =
          adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 1);

      assertThat(response.items().get(0).dueAt()).isEqualTo(BUNDLE_DUE_AT).isNotEqualTo(자리기한);
    }

    // LEGACY 는 묶음이 있어도 자리 값을 쓴다 — 이 구분이 무너지면 수동 입금확인·자동 취소가 멈춘다.
    @Test
    void LEGACY_결제목록은_묶음_기한을_보지_않는다() {
      Instant 자리기한 = BASE_TIME.plus(1, ChronoUnit.DAYS);
      AdminPaymentView legacy = view(1L, 10L, BASE_TIME);
      ReflectionTestUtils.setField(legacy.participation(), "bundleId", 900L);
      ReflectionTestUtils.setField(legacy.participation(), "dueAt", 자리기한);
      given(adminPaymentQueryRepository.findPayments(isNull(), isNull(), any(Cursor.class), eq(2)))
          .willReturn(List.of(legacy));
      given(adminPaymentQueryRepository.countConfirmedByBuncheolIds(List.of(10L)))
          .willReturn(List.of(new BuncheolConfirmedCount(10L, 1)));

      CursorResponse<AdminPaymentRecordResponse> response =
          adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 1);

      assertThat(response.items().get(0).dueAt()).isEqualTo(자리기한);
    }

    @Test
    void size보다_많이_조회되면_hasNext와_다음_커서를_돌려준다() {
      // given — size 2 요청 → limit 3 조회, 3건 반환이면 다음 페이지 존재
      List<AdminPaymentView> fetched =
          List.of(
              view(3L, 10L, BASE_TIME.plusSeconds(120)),
              view(2L, 10L, BASE_TIME.plusSeconds(60)),
              view(1L, 10L, BASE_TIME));
      given(
              adminPaymentQueryRepository.findPayments(
                  isNull(), isNull(), any(Cursor.class), eq(3)))
          .willReturn(fetched);
      given(adminPaymentQueryRepository.countConfirmedByBuncheolIds(List.of(10L)))
          .willReturn(List.of(new BuncheolConfirmedCount(10L, 1)));

      // when
      CursorResponse<AdminPaymentRecordResponse> response =
          adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 2);

      // then
      assertThat(response.hasNext()).isTrue();
      assertThat(response.items()).hasSize(2);
      assertThat(response.items().get(0).participationId()).isEqualTo(3L);
      assertThat(response.items().get(0).confirmedCount()).isEqualTo(1);
      assertThat(response.items().get(0).minHeadcount()).isEqualTo(2);
      assertThat(response.nextCursor())
          .isEqualTo(new Cursor(BASE_TIME.plusSeconds(60), 2L).encode());
    }

    @Test
    void 마지막_페이지면_nextCursor가_없다() {
      // given
      given(
              adminPaymentQueryRepository.findPayments(
                  isNull(), isNull(), any(Cursor.class), eq(21)))
          .willReturn(List.of(view(1L, 10L, BASE_TIME)));
      given(adminPaymentQueryRepository.countConfirmedByBuncheolIds(anyList()))
          .willReturn(List.of());

      // when
      CursorResponse<AdminPaymentRecordResponse> response =
          adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 20);

      // then
      assertThat(response.hasNext()).isFalse();
      assertThat(response.nextCursor()).isNull();
      // 확정 집계가 없는 분철은 0 으로 보정된다
      assertThat(response.items().getFirst().confirmedCount()).isZero();
    }

    @Test
    void 결과가_없으면_빈_응답을_돌려준다() {
      // given
      given(
              adminPaymentQueryRepository.findPayments(
                  any(), any(), any(Cursor.class), anyInt()))
          .willReturn(List.of());

      // when
      CursorResponse<AdminPaymentRecordResponse> response =
          adminPaymentQueryService.getPayments(
              AdminPaymentStatus.CONFIRMED, null, Cursor.firstPage(), 20);

      // then
      assertThat(response).isEqualTo(CursorResponse.empty());
      then(adminPaymentQueryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void 검색어의_LIKE_와일드카드를_이스케이프해서_넘긴다() {
      // given
      given(adminPaymentQueryRepository.findPayments(any(), any(), any(Cursor.class), anyInt()))
          .willReturn(List.of());

      // when
      adminPaymentQueryService.getPayments(null, " 50%_할인 ", Cursor.firstPage(), 20);

      // then — trim 후 이스케이프된 키워드로 조회한다
      then(adminPaymentQueryRepository)
          .should()
          .findPayments(isNull(), eq("50\\%\\_할인"), any(Cursor.class), eq(21));
    }

    @Test
    void size는_1과_100_사이로_보정된다() {
      // given
      given(adminPaymentQueryRepository.findPayments(any(), any(), any(Cursor.class), anyInt()))
          .willReturn(List.of());

      // when
      adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 0);
      adminPaymentQueryService.getPayments(null, null, Cursor.firstPage(), 1000);

      // then — limit 은 보정된 size + 1
      then(adminPaymentQueryRepository)
          .should()
          .findPayments(isNull(), isNull(), any(Cursor.class), eq(2));
      then(adminPaymentQueryRepository)
          .should()
          .findPayments(isNull(), isNull(), any(Cursor.class), eq(101));
    }
  }

  @Nested
  @DisplayName("getSummary 테스트")
  class GetSummaryTest {

    @Test
    void 통계를_응답으로_변환한다() {
      // given
      given(adminPaymentQueryRepository.summarize())
          .willReturn(new AdminPaymentSummary(2, 3, 1, 4, 10, 33000));

      // when
      AdminPaymentSummaryResponse response = adminPaymentQueryService.getSummary();

      // then
      assertThat(response)
          .isEqualTo(new AdminPaymentSummaryResponse(2, 3, 1, 4, 10, 33000));
    }
  }
}
