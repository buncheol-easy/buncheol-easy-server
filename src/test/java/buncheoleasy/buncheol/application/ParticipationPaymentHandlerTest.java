package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
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
@DisplayName("ParticipationPaymentHandler 단위 테스트")
class ParticipationPaymentHandlerTest {

  @InjectMocks private ParticipationPaymentHandler participationPaymentHandler;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC);

  private static final Long PARTICIPATION_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;

  private Participation newAwaitingBalanceParticipation() {
    Participation participation =
        Participation.create(1L, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, 30_000L);
    setId(participation, PARTICIPATION_ID);
    setStatus(participation, ParticipationStatus.AWAITING_PAYMENT);
    return participation;
  }

  @Nested
  @DisplayName("참여 객체 소유권 검증 테스트")
  class ValidateOwnershipTest {

    @Test
    void 소유자이면_예외가_발생하지_않는다() {
      Participation participation = newAwaitingBalanceParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      participationPaymentHandler.validateOwnership(PARTICIPATION_ID, PARTICIPANT_ID);
    }

    @Test
    void 소유자가_아니면_예외가_발생한다() {
      Participation participation = newAwaitingBalanceParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      assertThatThrownBy(() -> participationPaymentHandler.validateOwnership(PARTICIPATION_ID, 999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("결제 완료 처리 테스트")
  class OnPaymentCompletedTest {

    @Test
    void 낙찰자_결제_완료시_참여가_확정되고_배송_스냅샷_생성을_위임한다() {
      Participation participation = newAwaitingBalanceParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isNotNull();
      then(deliverySnapshotCreator).should().create(participation);
    }
  }

  @Nested
  @DisplayName("결제 실패 처리 테스트")
  class OnPaymentFailedTest {

    @Test
    void 낙찰자_결제_실패시_참여_상태를_변경하지_않는다() {
      participationPaymentHandler.onPaymentFailed(PARTICIPATION_ID, "결제 실패");

      then(participationDomainService).should(never()).getParticipation(any());
      then(participationDomainService).should(never()).updateParticipationStatus(any(), any());
    }
  }

  private void setId(final Participation participation, final Long id) {
    setFieldValue(participation, "id", id);
  }

  private void setStatus(final Participation participation, final ParticipationStatus status) {
    setFieldValue(participation, "status", status);
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
