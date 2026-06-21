package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationDomainService 단위 테스트")
class ParticipationDomainServiceTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");
  private static final Instant DUE_AT = Instant.parse("2026-05-14T12:30:00Z");

  @InjectMocks private ParticipationDomainService participationDomainService;

  @Mock private ParticipationRepository participationRepository;

  private static Participation newParticipation() {
    return Participation.create(1L, 10L, 100L, 200L, 30_000L, REFUND_ACCOUNT, DUE_AT);
  }

  @Nested
  @DisplayName("참여 생성 테스트")
  class CreateParticipationIfRecruitingTest {

    @Test
    void 모집중인_분철에_참여_생성에_성공한다() {
      Participation participation = newParticipation();
      given(participationRepository.saveIfRecruiting(participation)).willReturn(true);

      boolean result = participationDomainService.createParticipationIfRecruiting(participation);

      assertThat(result).isTrue();
    }

    @Test
    void 모집중이_아닌_분철이면_false를_반환한다() {
      Participation participation = newParticipation();
      given(participationRepository.saveIfRecruiting(participation)).willReturn(false);

      boolean result = participationDomainService.createParticipationIfRecruiting(participation);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("참여 조회 테스트")
  class GetParticipationTest {

    @Test
    void 존재하는_참여를_조회한다() {
      Participation participation = newParticipation();
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(participation));

      Participation result = participationDomainService.getParticipation(PARTICIPATION_ID);

      assertThat(result).isSameAs(participation);
    }

    @Test
    void 존재하지_않는_참여를_조회하면_예외가_발생한다() {
      given(participationRepository.findById(999L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> participationDomainService.getParticipation(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("분철별 참여 조회/집계 위임 테스트")
  class FindByBuncheolIdTest {

    @Test
    void 활성_참여_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findActiveByBuncheolId(1L))
          .willReturn(List.of(participation));

      List<Participation> result = participationDomainService.findActiveByBuncheolId(1L);

      assertThat(result).containsExactly(participation);
    }

    @Test
    void 입금확인된_참여_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findConfirmedByBuncheolId(1L))
          .willReturn(List.of(participation));

      List<Participation> result = participationDomainService.findConfirmedByBuncheolId(1L);

      assertThat(result).containsExactly(participation);
    }

    @Test
    void 입금확인된_참여_수_집계를_리포지토리에_위임한다() {
      given(participationRepository.countConfirmedByBuncheolId(1L)).willReturn(3);

      assertThat(participationDomainService.countConfirmedByBuncheolId(1L)).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("참여자의 활성 참여 존재 여부 테스트")
  class HasActiveParticipationByTest {

    @Test
    void 활성_참여가_있으면_true를_반환한다() {
      given(participationRepository.existsActiveByParticipantId(100L)).willReturn(true);

      assertThat(participationDomainService.hasActiveParticipationBy(100L)).isTrue();
    }

    @Test
    void 활성_참여가_없으면_false를_반환한다() {
      given(participationRepository.existsActiveByParticipantId(100L)).willReturn(false);

      assertThat(participationDomainService.hasActiveParticipationBy(100L)).isFalse();
    }
  }

  @Nested
  @DisplayName("입금 만료 대상 조회 테스트")
  class FindOverduePaymentTargetsTest {

    @Test
    void 입금_만료_대상_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findOverduePaymentTargets(NOW, 200))
          .willReturn(List.of(participation));

      List<Participation> result =
          participationDomainService.findOverduePaymentTargets(NOW, 200);

      assertThat(result).containsExactly(participation);
    }
  }

  @Nested
  @DisplayName("호스트 입금확인 테스트")
  class ConfirmPaymentTest {

    @Test
    void CAS_성공시_별도_재조회_없이_통과한다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(true);

      participationDomainService.confirmPayment(PARTICIPATION_ID, NOW);

      then(participationRepository).should(never()).findById(PARTICIPATION_ID);
    }

    @Test
    void CAS_실패_후_여전히_입금확인중이면_기한_경과_예외를_던진다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(awaitingParticipation()));

      assertThatThrownBy(() -> participationDomainService.confirmPayment(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED);
    }

    @Test
    void CAS_실패_후_이미_취소_확정됐으면_상태_위반_예외를_던진다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(participationWithStatus(ParticipationStatus.CANCELLED)));

      assertThatThrownBy(() -> participationDomainService.confirmPayment(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("참여자 자발 취소 테스트")
  class CancelByParticipantTest {

    @Test
    void CAS_성공시_예외_없이_통과한다() {
      given(participationRepository.cancelByParticipantIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(true);

      participationDomainService.cancelByParticipant(PARTICIPATION_ID, NOW);

      then(participationRepository).should().cancelByParticipantIfAwaiting(PARTICIPATION_ID, NOW);
    }

    @Test
    void CAS_실패시_상태_위반_예외를_던진다() {
      given(participationRepository.cancelByParticipantIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);

      assertThatThrownBy(
              () -> participationDomainService.cancelByParticipant(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("입금 만료 처리 테스트")
  class ExpireOverduePaymentTest {

    @Test
    void CAS_성공시_true를_반환한다() {
      given(participationRepository.expireIfOverdue(PARTICIPATION_ID, NOW)).willReturn(true);

      assertThat(participationDomainService.expireOverduePayment(PARTICIPATION_ID, NOW)).isTrue();
    }

    @Test
    void CAS_실패시_예외_없이_false를_반환한다() {
      given(participationRepository.expireIfOverdue(PARTICIPATION_ID, NOW)).willReturn(false);

      assertThat(participationDomainService.expireOverduePayment(PARTICIPATION_ID, NOW)).isFalse();
    }
  }

  @Nested
  @DisplayName("분철 취소 cascade — 활성 참여 일괄 전이 테스트")
  class CancelActiveByBuncheolIdTest {

    @Test
    void 리포지토리에_그대로_위임하고_갱신_행_수를_반환한다() {
      given(participationRepository.cancelActiveByBuncheolId(1L, NOW)).willReturn(3);

      int affected = participationDomainService.cancelActiveByBuncheolId(1L, NOW);

      assertThat(affected).isEqualTo(3);
      then(participationRepository).should().cancelActiveByBuncheolId(1L, NOW);
    }
  }

  private static Participation awaitingParticipation() {
    return participationWithStatus(ParticipationStatus.AWAITING_PAYMENT);
  }

  private static Participation participationWithStatus(final ParticipationStatus status) {
    Participation participation = newParticipation();
    setField(participation, "status", status);
    return participation;
  }

  private static void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = Participation.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
