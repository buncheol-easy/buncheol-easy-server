package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
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
  @Mock private BuncheolDomainService buncheolDomainService;
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

    @Test
    @DisplayName("확인 가능 슬롯 전부를 한 번에 확정한다")
    void confirmsAllPayableSlots() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(2);

      List<Long> confirmed =
          participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(233L, 232L));

      assertThat(confirmed).containsExactly(232L, 233L);
      ArgumentCaptor<BundlePaymentConfirmedEvent> captor =
          ArgumentCaptor.forClass(BundlePaymentConfirmedEvent.class);
      then(eventPublisher).should().publishEvent(captor.capture());
      assertThat(captor.getValue().confirmedParticipationIds()).containsExactly(232L, 233L);
    }

    // 모두 입금 확인돼도 분철은 저절로 진행 확정되지 않고, 배송도 여기서 만들지 않는다 — 개최자의 진행 확정 때 생긴다.
    @Test
    @DisplayName("묶음 입금확인은 배송을 만들지 않고 분철을 진행확정하지 않는다")
    void doesNotCreateDeliveryNorConfirmBuncheol() {
      stubBundleAndLockedBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
      stubPayable(232L, 233L);
      given(participationRepository.confirmBundleIfPayable(BUNDLE_ID, NOW)).willReturn(2);

      participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(232L, 233L));

      then(buncheolDomainService).should(never()).confirmIfCollecting(anyLong(), any());
      then(participationRepository).should(never()).findAllByBundleIds(any());
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

      assertThat(participationBundleService.confirmPayment(HOST_ID, BUNDLE_ID, List.of(233L, 232L)))
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
