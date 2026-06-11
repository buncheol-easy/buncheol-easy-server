package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HostPaymentService 단위 테스트")
class HostPaymentServiceTest {

  @InjectMocks private HostPaymentService hostPaymentService;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;
  @Mock private Buncheol buncheol;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC);

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long HOST_ID = 10L;
  private static final Long WRONG_HOST_ID = 999L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 50L;
  private static final long BID_AMOUNT = 30_000L;
  private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");
  private static final Instant FUTURE_DUE = Instant.parse("2026-03-12T12:00:00Z");

  private static Participation newParticipation() {
    return Participation.create(BUNCHEOL_ID, 20L, PARTICIPANT_ID, 200L, BID_AMOUNT);
  }

  private Participation reportedParticipation() {
    Participation participation = newParticipation();
    setId(participation, PARTICIPATION_ID);
    participation.awardAsWinner(FUTURE_DUE); // AWAITING_PAYMENT
    participation.reportPayment(NOW, 200L); // PAYMENT_REPORTED
    return participation;
  }

  @Nested
  @DisplayName("개최자 수동 입금확인 테스트")
  class ConfirmPaymentTest {

    @Test
    void PAYMENT_REPORTED_상태를_개최자가_확인하면_CONFIRMED_로_전환하고_배송_스냅샷을_생성한다() {
      Participation participation = reportedParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      hostPaymentService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      then(buncheol).should().validateOwner(HOST_ID);
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getPaymentConfirmedAt()).isEqualTo(NOW);
      then(deliverySnapshotCreator).should().create(participation);
    }

    @Test
    void 개최자가_아니면_예외가_발생하고_스냅샷을_생성하지_않는다() {
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(WRONG_HOST_ID);

      assertThatThrownBy(() -> hostPaymentService.confirmPayment(WRONG_HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(deliverySnapshotCreator).shouldHaveNoInteractions();
    }

    @Test
    void PAYMENT_REPORTED_상태가_아니면_상태_전환_예외가_발생하고_스냅샷을_생성하지_않는다() {
      Participation participation = newParticipation(); // ACTIVE_BID (기본)
      setId(participation, PARTICIPATION_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      assertThatThrownBy(() -> hostPaymentService.confirmPayment(HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);

      then(deliverySnapshotCreator).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("미입금 낙찰자 만료 테스트")
  class ExpirePaymentTest {

    @Test
    void 개최자가_호출하면_권한_검증_후_도메인서비스에_만료_승계를_위임한다() {
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      hostPaymentService.expirePayment(HOST_ID, PARTICIPATION_ID);

      then(buncheol).should().validateOwner(HOST_ID);
      then(participationDomainService).should().expireWinnerAndPromoteNext(participation, NOW);
    }

    @Test
    void 개최자가_아니면_예외가_발생하고_만료를_수행하지_않는다() {
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(WRONG_HOST_ID);

      assertThatThrownBy(() -> hostPaymentService.expirePayment(WRONG_HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(participationDomainService)
          .should(never())
          .expireWinnerAndPromoteNext(any(), any());
    }
  }

  private void setId(final Participation participation, final Long id) {
    setFieldValue(participation, "id", id);
  }

  private void setFieldValue(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
