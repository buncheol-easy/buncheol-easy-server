package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationBundleService — 개최자 「제외」")
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
    Buncheol buncheol = org.mockito.Mockito.mock(Buncheol.class);
    lenient().when(buncheol.isC2c()).thenReturn(true);
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

  private void stubBundleAndBuncheol(final Instant dueAt, final Buncheol buncheol) {
    given(participationBundleDomainService.findById(BUNDLE_ID))
        .willReturn(Optional.of(bundle(dueAt)));
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
  }

  @Test
  @DisplayName("기한이 지났으면 제외하고 실제로 취소된 슬롯을 돌려준다")
  void releasesAndReturnsActuallyCancelledSlots() {
    stubBundleAndBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.AWAITING_PAYMENT, null, null)));
    given(participationRepository.findAllByBundleIds(List.of(BUNDLE_ID)))
        // CAS 이후 재조회 — 실제로 취소된 것만 걸러진다.
        .willReturn(
            List.of(
                slot(232L, ParticipationStatus.CANCELLED, ParticipationCancelReason.HOST_RELEASED, NOW),
                // 그 사이 참여자가 스스로 취소한 슬롯. 「제외」가 건드리지 않았으므로 목록에 들어가면 안 된다.
                slot(233L, ParticipationStatus.CANCELLED, ParticipationCancelReason.USER_CANCELLED, NOW)));
    given(participationRepository.releaseBundleIfDue(BUNDLE_ID, NOW)).willReturn(1);

    List<Long> released = participationBundleService.release(HOST_ID, BUNDLE_ID);

    assertThat(released).containsExactly(232L);
    ArgumentCaptor<BundleReleasedEvent> captor =
        ArgumentCaptor.forClass(BundleReleasedEvent.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue().releasedParticipationIds()).containsExactly(232L);
  }

  // 🔴 staging 회귀: cancelled_at 은 초 단위 DATETIME 이라 나노초를 가진 now 와 equals 로 비교하면
  // 영원히 안 맞는다. 「제외」는 성공했는데 응답의 취소 목록이 빈 배열로 나왔다.
  // H2 는 정밀도를 보존해 테스트가 통과했으므로, DB 가 잘라 쓴 값을 흉내 내 고정한다.
  @Test
  @DisplayName("DB 가 시각을 초 단위로 잘라 저장해도 취소분을 찾아낸다")
  void findsReleasedSlotsEvenWhenTimestampTruncatedBySeconds() {
    stubBundleAndBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.AWAITING_PAYMENT, null, null)));
    given(participationRepository.releaseBundleIfDue(BUNDLE_ID, NOW_WITH_NANOS)).willReturn(1);
    given(participationRepository.findAllByBundleIds(List.of(BUNDLE_ID)))
        .willReturn(
            List.of(
                slot(
                    232L,
                    ParticipationStatus.CANCELLED,
                    ParticipationCancelReason.HOST_RELEASED,
                    // DB 가 잘라 저장한 값 — 서비스가 넘긴 now 와 나노초가 다르다.
                    NOW_WITH_NANOS.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));

    assertThat(participationBundleService.release(HOST_ID, BUNDLE_ID)).containsExactly(232L);
  }

  @Test
  @DisplayName("남의 분철 묶음은 제외할 수 없다")
  void rejectsOtherHost() {
    stubBundleAndBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));

    assertThatThrownBy(() -> participationBundleService.release(OTHER_HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class);
    then(participationRepository).should(never()).releaseBundleIfDue(anyLong(), any());
  }

  @Test
  @DisplayName("LEGACY 분철은 제외 대상이 아니다")
  void rejectsLegacy() {
    Buncheol legacy = org.mockito.Mockito.mock(Buncheol.class);
    lenient().when(legacy.isC2c()).thenReturn(false);
    stubBundleAndBuncheol(NOW.minusSeconds(3600), legacy);

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED.getMessage());
  }

  @Test
  @DisplayName("모집 중이면 사유가 드러나는 에러로 막는다")
  void rejectsWhileRecruiting() {
    stubBundleAndBuncheol(null, c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.APPLIED, null, null)));

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.BUNDLE_RELEASE_RECRUITING.getMessage());
    then(participationRepository).should(never()).releaseBundleIfDue(anyLong(), any());
  }

  @Test
  @DisplayName("입금 기한 전이면 사유가 드러나는 에러로 막는다")
  void rejectsBeforeDue() {
    stubBundleAndBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.PAYMENT_SENT, null, null)));

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.BUNDLE_RELEASE_BEFORE_DUE.getMessage());
  }

  @Test
  @DisplayName("입금확인된 슬롯이 있으면 사유가 드러나는 에러로 막는다")
  void rejectsWithConfirmedSlot() {
    stubBundleAndBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.CONFIRMED, null, null)));

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.BUNDLE_RELEASE_HAS_CONFIRMED.getMessage());
  }

  // 판정은 통과했는데 CAS 가 0행이면 그 사이 상태가 바뀐 것 — 화면을 새로 고치게 한다.
  @Test
  @DisplayName("판정 통과 후 CAS 가 0행이면 상태 충돌로 막는다")
  void rejectsWhenCasAffectsNothing() {
    stubBundleAndBuncheol(NOW.minusSeconds(3600), c2cBuncheol(HOST_ID));
    given(participationRepository.findAllByBundleIdForUpdate(BUNDLE_ID))
        .willReturn(List.of(slot(232L, ParticipationStatus.AWAITING_PAYMENT, null, null)));
    given(participationRepository.releaseBundleIfDue(BUNDLE_ID, NOW)).willReturn(0);

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class);
    then(eventPublisher).should(never()).publishEvent(any(BundleReleasedEvent.class));
  }

  @Test
  @DisplayName("없는 묶음이면 404")
  void rejectsMissingBundle() {
    given(participationBundleDomainService.findById(BUNDLE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> participationBundleService.release(HOST_ID, BUNDLE_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.BUNDLE_NOT_FOUND.getMessage());
  }
  @org.junit.jupiter.api.Nested
  @DisplayName("markPaymentSent — 참여자 묶음 「보냈어요」")
  class MarkPaymentSentTest {

    private static final Long PARTICIPANT_ID = 10L;

    @Test
    @DisplayName("실제로 마킹된 슬롯만 돌려주고 알린다")
    void marksAndReturnsActuallyMarked() {
      stubBundleAndBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(2);
      given(participationRepository.findAllByBundleIds(List.of(BUNDLE_ID)))
          .willReturn(
              List.of(
                  sentSlot(232L, NOW),
                  sentSlot(233L, NOW),
                  // 다른 호출에서 이미 마킹된 슬롯 — 이번 알림에 섞이면 안 된다.
                  sentSlot(234L, NOW.minusSeconds(600))));

      List<Long> marked = participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID);

      assertThat(marked).containsExactly(232L, 233L);
      ArgumentCaptor<BundlePaymentSentEvent> captor =
          ArgumentCaptor.forClass(BundlePaymentSentEvent.class);
      then(eventPublisher).should().publishEvent(captor.capture());
      assertThat(captor.getValue().markedParticipationIds()).containsExactly(232L, 233L);
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
      stubBundleAndBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(0);
      given(participationRepository.findAllByBundleIds(List.of(BUNDLE_ID)))
          .willReturn(List.of(sentSlot(232L, NOW.minusSeconds(600))));

      assertThat(participationBundleService.markPaymentSent(PARTICIPANT_ID, BUNDLE_ID))
          .containsExactly(232L);
      then(eventPublisher).should(never()).publishEvent(any(BundlePaymentSentEvent.class));
    }

    @Test
    @DisplayName("마킹할 슬롯이 하나도 없으면 상태 위반으로 막는다")
    void rejectsWhenNothingToMark() {
      stubBundleAndBuncheol(NOW.plusSeconds(3600), c2cBuncheol(HOST_ID));
      given(participationRepository.markBundlePaymentSent(BUNDLE_ID, NOW)).willReturn(0);
      given(participationRepository.findAllByBundleIds(List.of(BUNDLE_ID))).willReturn(List.of());

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

}
