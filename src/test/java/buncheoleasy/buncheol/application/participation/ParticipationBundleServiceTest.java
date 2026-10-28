package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.application.BuncheolConfirmer;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationBundleService — 묶음 단위 개최자·참여자 조작")
class ParticipationBundleServiceTest {

  // 실제 운영은 나노초를 가진 시각을 넘긴다 — 초 단위로 떨어지는 값으로 테스트하면 잘림 문제가 안 드러난다.
  private static final Instant NOW_WITH_NANOS = Instant.parse("2026-08-31T12:00:00.123456789Z");
  private static final Instant NOW = NOW_WITH_NANOS;
  private static final Long HOST_ID = 1L;
  private static final Long OTHER_HOST_ID = 999L;
  private static final Long BUNDLE_ID = 141L;
  private static final Long BUNCHEOL_ID = 104L;

  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private ParticipationRepository participationRepository;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolConfirmer buncheolConfirmer;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @InjectMocks private ParticipationBundleService participationBundleService;

  private static ParticipationBundle bundle(final Instant dueAt) {
    ParticipationBundle bundle =
        ParticipationBundle.open(
            BUNCHEOL_ID, 10L, 1L, 3_000L, new RefundAccount("국민", "1234", "홍길동"), dueAt);
    setField(bundle, "id", BUNDLE_ID);
    return bundle;
  }

  private static Participation slot(
      final long id, final ParticipationStatus status, final ParticipationCancelReason reason,
      final Instant cancelledAt) {
    Participation participation =
        Participation.createApplied(BUNCHEOL_ID, 500L + id, 10L, 1L, 10_000L, 0L);
    setField(participation, "id", id);
    setField(participation, "bundleId", BUNDLE_ID);
    setField(participation, "status", status);
    setField(participation, "cancelReason", reason);
    setField(participation, "cancelledAt", cancelledAt);
    return participation;
  }

  private Buncheol c2cBuncheol(final Long hostId) {
    Buncheol buncheol = mock(Buncheol.class);
    lenient().when(buncheol.isC2c()).thenReturn(true);
    lenient().when(buncheol.getId()).thenReturn(BUNCHEOL_ID);
    lenient()
        .doAnswer(
            invocation -> {
              if (!hostId.equals(invocation.getArgument(0))) {
                throw new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION);
              }
              return null;
            })
        .when(buncheol)
        .validateOwner(anyLong());
    return buncheol;
  }

  // 묶음 「보냈어요」·입금 확인은 분철 행을 첫 잠금으로 잡는다(잠금 조회).
  private void stubBundleAndLockedBuncheol(final Instant dueAt, final Buncheol buncheol) {
    given(participationBundleDomainService.findById(BUNDLE_ID))
        .willReturn(Optional.of(bundle(dueAt)));
    given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
  }

  @Nested
  @DisplayName("markPaymentSent — 참여자 묶음 「보냈어요」")
  class MarkPaymentSentTest {

    private static final Long PARTICIPANT_ID = 10L;

    @Test
    @DisplayName("응답은 이번 호출 마킹분, 알림은 묶음 전체 마킹분으로 나간다")
    void respondsWithThisCallButNotifiesWholeBundle() {
      stubBundleAndLockedBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(1);
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
          .willReturn(
              List.of(
                  // 이번 호출이 마킹 (DB 가 초 단위로 잘라 저장한 값)
                  sentSlot(233L, NOW.truncatedTo(ChronoUnit.SECONDS)),
                  // 이미 마킹돼 있던 슬롯(지금 코드로는 생기지 않는 방어 케이스) — 알림 금액에는 포함돼야 한다
                  sentSlot(232L, NOW.minusSeconds(600))));

      List<Long> marked = participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID);

      assertThat(marked).containsExactly(233L);
      ArgumentCaptor<BundlePaymentSentEvent> captor =
          ArgumentCaptor.forClass(BundlePaymentSentEvent.class);
      then(eventPublisher).should().publishEvent(captor.capture());
      assertThat(captor.getValue().markedParticipationIds()).containsExactly(233L);
      // 🔴 이번 호출분만 합산하면 개최자가 실제 이체액보다 작은 금액으로 대조하게 된다.
      assertThat(captor.getValue().sentParticipationIds()).containsExactly(233L, 232L);
    }

    // 🔴 REPEATABLE READ 회귀: 동시 더블탭에서 UPDATE 는 0행인데 일반 조회는 옛 스냅샷을 봐 409 가 났다.
    // 잠금 조회(current read)라야 이미 마킹된 것을 보고 멱등 성공한다.
    @Test
    @DisplayName("동시 더블탭에서 0행이어도 잠금 조회로 이미 마킹된 것을 보고 멱등 성공한다")
    void idempotentUnderConcurrentDoubleTap() {
      stubBundleAndLockedBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(0);
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
          .willReturn(List.of(sentSlot(232L, NOW.truncatedTo(ChronoUnit.SECONDS))));

      assertThat(participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .containsExactly(232L);
      then(eventPublisher).should(never()).publishEvent(any(BundlePaymentSentEvent.class));
    }

    @Test
    @DisplayName("LEGACY 분철의 묶음은 마킹 대상이 아니다")
    void rejectsLegacyBundle() {
      Buncheol legacy = mock(Buncheol.class);
      lenient().when(legacy.isC2c()).thenReturn(false);
      stubBundleAndLockedBuncheol(NOW.plusSeconds(3600), legacy);

      assertThatThrownBy(
              () -> participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED.getMessage());
    }

    @Test
    @DisplayName("없는 묶음이면 404")
    void rejectsMissingBundleOnMark() {
      given(participationBundleDomainService.findById(BUNDLE_ID)).willReturn(Optional.empty());

      assertThatThrownBy(
              () -> participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.BUNDLE_NOT_FOUND.getMessage());
    }

    // 🔴 묶음 id 는 AUTO_INCREMENT 라 추측 가능하다 — 남의 묶음을 마킹할 수 없어야 한다.
    @Test
    @DisplayName("남의 묶음은 마킹할 수 없다")
    void rejectsOtherParticipant() {
      given(participationBundleDomainService.findById(BUNDLE_ID))
          .willReturn(Optional.of(bundle(NOW.plusSeconds(3600))));

      assertThatThrownBy(() -> participationBundleService.markPaymentSent(999L, BUNDLE_ID))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.PARTICIPATION_NO_PERMISSION.getMessage());
      then(participationRepository).should(never()).markBundlePaymentSent(anyLong(), any());
    }

    // 더블탭으로 개최자 알림이 두 번 가면 안 된다.
    @Test
    @DisplayName("이미 전부 마킹됐으면 멱등 성공하고 알림을 다시 보내지 않는다")
    void idempotentWhenAlreadyMarked() {
      stubBundleAndLockedBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(0);
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
          .willReturn(List.of(sentSlot(232L, NOW.minusSeconds(600))));

      assertThat(participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .containsExactly(232L);
      then(eventPublisher).should(never()).publishEvent(any(BundlePaymentSentEvent.class));
    }

    @Test
    @DisplayName("마킹할 슬롯이 하나도 없으면 상태 위반으로 막는다")
    void rejectsWhenNothingToMark() {
      stubBundleAndLockedBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(0);
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID)).willReturn(List.of());

      assertThatThrownBy(
              () -> participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(
              ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED.getMessage());
    }

    private Participation sentSlot(final long id, final Instant sentAt) {
      Participation participation = slot(id, ParticipationStatus.PAYMENT_SENT, null, null);
      setField(participation, "paymentSentAt", sentAt);
      return participation;
    }
  }

  @Nested
  @DisplayName("confirmPayment — 개최자 묶음 입금확인 (all-or-nothing)")
  class ConfirmPaymentTest {

    private void stubPayable(final Long... ids) {
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
          .willReturn(
              Arrays.stream(ids)
                  .map(id -> slot(id, ParticipationStatus.AWAITING_PAYMENT, null, null))
                  .toList());
    }

    /** 이 묶음을 확인한 뒤 분철 행 잠금 아래에서 다시 센 살아 있는 참여. */
    private void stubActiveAfter(final Participation... participations) {
      given(participationDomainService.findActiveByBuncheolIdForUpdate(BUNCHEOL_ID))
          .willReturn(List.of(participations));
    }

    private Buncheol collectingBuncheol() {
      Buncheol buncheol = c2cBuncheol(HOST_ID);
      lenient().when(buncheol.getStatus()).thenReturn(BuncheolStatus.PAYMENT_COLLECTING);
      lenient().when(buncheol.getPaymentDueAt()).thenReturn(NOW.plusSeconds(3600));
      return buncheol;
    }

    @Test
    @DisplayName("확인 가능 슬롯 전부를 한 번에 확정한다")
    void confirmsAllPayableSlots() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), collectingBuncheol());
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(2);
      stubActiveAfter(
          slot(232L, ParticipationStatus.CONFIRMED, null, null),
          slot(233L, ParticipationStatus.CONFIRMED, null, null),
          slot(300L, ParticipationStatus.AWAITING_PAYMENT, null, null));

      BundleConfirmResult result =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(233L, 232L));

      assertThat(result.confirmedParticipationIds()).containsExactly(232L, 233L);
      ArgumentCaptor<BundlePaymentConfirmedEvent> captor =
          ArgumentCaptor.forClass(BundlePaymentConfirmedEvent.class);
      then(eventPublisher).should().publishEvent(captor.capture());
      assertThat(captor.getValue().confirmedParticipationIds()).containsExactly(232L, 233L);
    }

    // 🔴 이 묶음 대조만으로는 모른다 — 다른 묶음은 분철 행을 기다리는 사이 바뀌었을 수 있어 잠금 아래에서 다시 센다.
    @Test
    @DisplayName("마지막 미확인 묶음이면 같은 트랜잭션에서 다시 센 참여로 진행 확정하고 true 를 돌려준다")
    void confirmsBuncheolWhenLastUnconfirmedBundle() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), collectingBuncheol());
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(2);
      List<Participation> active =
          List.of(
              slot(232L, ParticipationStatus.CONFIRMED, null, null),
              slot(233L, ParticipationStatus.CONFIRMED, null, null),
              slot(300L, ParticipationStatus.CONFIRMED, null, null));
      given(participationDomainService.findActiveByBuncheolIdForUpdate(BUNCHEOL_ID))
          .willReturn(active);

      BundleConfirmResult result =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L, 233L));

      assertThat(result.buncheolConfirmed()).isTrue();
      // 「입금 확인 안내」를 먼저 내고 진행 확정(그 안의 진행확정 알림)이 뒤에 온다.
      InOrder order =
          inOrder(participationRepository, eventPublisher, participationDomainService, buncheolConfirmer);
      order.verify(participationRepository).confirmBundleIfPayable(BUNDLE_ID, NOW);
      order.verify(eventPublisher).publishEvent(any(BundlePaymentConfirmedEvent.class));
      order.verify(participationDomainService).findActiveByBuncheolIdForUpdate(BUNCHEOL_ID);
      order.verify(buncheolConfirmer).confirm(BUNCHEOL_ID, active, NOW);
    }

    @Test
    @DisplayName("다른 묶음에 입금 대기가 남아 있으면 진행 확정하지 않고 false 를 돌려준다")
    void doesNotConfirmBuncheolWhileOtherBundleAwaits() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), collectingBuncheol());
      stubPayable(232L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(1);
      stubActiveAfter(
          slot(232L, ParticipationStatus.CONFIRMED, null, null),
          slot(300L, ParticipationStatus.AWAITING_PAYMENT, null, null));

      BundleConfirmResult result =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L));

      assertThat(result.buncheolConfirmed()).isFalse();
      then(buncheolConfirmer).shouldHaveNoInteractions();
    }

    // 「보냈어요」는 참여자의 신고일 뿐 입금 확인이 아니다.
    @Test
    @DisplayName("남은 것이 「보냈어요」뿐이어도 진행 확정하지 않는다")
    void doesNotConfirmBuncheolWhileOtherBundleOnlySent() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), collectingBuncheol());
      stubPayable(232L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(1);
      stubActiveAfter(
          slot(232L, ParticipationStatus.CONFIRMED, null, null),
          slot(300L, ParticipationStatus.PAYMENT_SENT, null, null));

      BundleConfirmResult result =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L));

      assertThat(result.buncheolConfirmed()).isFalse();
      then(buncheolConfirmer).shouldHaveNoInteractions();
    }

    // 0원 묶음도 입금 확인을 거치는 묶음이다 — 알림만 건너뛸 뿐 진행 확정 판정은 같다.
    @Test
    @DisplayName("0원 묶음이 마지막이어도 진행 확정한다")
    void confirmsBuncheolWhenLastBundleIsFree() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), collectingBuncheol());
      Participation free =
          Participation.createApplied(BUNCHEOL_ID, 732L, 10L, 1L, 0L, 0L);
      setField(free, "id", 232L);
      setField(free, "bundleId", BUNDLE_ID);
      setField(free, "status", ParticipationStatus.AWAITING_PAYMENT);
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID)).willReturn(List.of(free));
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(1);
      List<Participation> active =
          List.of(
              slot(232L, ParticipationStatus.CONFIRMED, null, null),
              slot(300L, ParticipationStatus.CONFIRMED, null, null));
      given(participationDomainService.findActiveByBuncheolIdForUpdate(BUNCHEOL_ID))
          .willReturn(active);

      BundleConfirmResult result =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L));

      assertThat(result.buncheolConfirmed()).isTrue();
      then(buncheolConfirmer).should().confirm(BUNCHEOL_ID, active, NOW);
    }

    // 🔴 개최자가 보지 못한 슬롯까지 확정되면 안 된다.
    @Test
    @DisplayName("화면이 본 슬롯 집합과 다르면 409 로 막고 확정하지 않는다")
    void rejectsWhenSlotsChanged() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      stubPayable(232L, 233L);

      assertThatThrownBy(
              () -> participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.BUNDLE_SLOTS_CHANGED.getMessage());
      then(participationRepository).should(never()).confirmBundleIfPayable(anyLong(), any());
    }

    // 순서는 화면이 보장하지 않는다 — 집합으로 대조해야 한다.
    @Test
    @DisplayName("순서가 달라도 같은 집합이면 통과한다")
    void ignoresOrderOfExpectedSlotIds() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(2);

      assertThat(
              participationBundleService
                  .confirmPayment(HOST_ID, BUNDLE_ID, List.of(233L, 232L))
                  .confirmedParticipationIds())
          .containsExactly(232L, 233L);
    }

    @Test
    @DisplayName("확인할 슬롯이 없으면 확정하지 않는다")
    void rejectsWhenNothingPayable() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
          .willReturn(List.of(slot(232L, ParticipationStatus.CONFIRMED, null, null)));

      assertThatThrownBy(
              () -> participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.BUNDLE_CONFIRM_NOT_ALLOWED.getMessage());
    }

    @Test
    @DisplayName("남의 분철 묶음은 확인할 수 없다")
    void rejectsOtherHostConfirm() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));

      assertThatThrownBy(
              () ->
                  participationBundleService.confirmPayment(
                      OTHER_HOST_ID, BUNDLE_ID, List.of(232L)))
          .isInstanceOf(BusinessException.class);
      then(participationRepository).should(never()).confirmBundleIfPayable(anyLong(), any());
    }

    // LEGACY 는 1인 1활성슬롯이라 묶음 = 참여다. 열어 두면 페이액션·배송 경로까지 검증해야 한다.
    @Test
    @DisplayName("LEGACY 분철은 묶음 확인 대상이 아니다")
    void rejectsLegacyConfirm() {
      Buncheol legacy = mock(Buncheol.class);
      lenient().when(legacy.isC2c()).thenReturn(false);
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), legacy);

      assertThatThrownBy(
              () -> participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED.getMessage());
    }

    // 부분 확정이 남으면 개최자가 돈 계산을 못 한다 — 롤백해야 한다.
    @Test
    @DisplayName("CAS 가 일부만 바꾸면 롤백한다")
    void rejectsPartialConfirm() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(1);

      assertThatThrownBy(
              () ->
                  participationBundleService.confirmPayment(
                      HOST_ID, BUNDLE_ID, List.of(232L, 233L)))
          .isInstanceOf(BusinessException.class);
      then(eventPublisher).should(never()).publishEvent(any(BundlePaymentConfirmedEvent.class));
    }
  }

}
