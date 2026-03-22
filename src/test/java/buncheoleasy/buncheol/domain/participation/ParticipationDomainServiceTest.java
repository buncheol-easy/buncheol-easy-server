package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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

  @InjectMocks private ParticipationDomainService participationDomainService;

  @Mock private ParticipationRepository participationRepository;

  @Nested
  @DisplayName("즉시 구매 참여 생성 테스트")
  class CreateInstantParticipationIfRecruitingTest {

    @Test
    void 모집중인_분철에_즉시_구매_참여_생성에_성공한다() {
      // given
      Participation participation = Participation.createInstant(1L, 10L, 100L, 200L, 50_000L);
      given(participationRepository.saveInstantIfRecruiting(participation)).willReturn(true);

      // when
      boolean result =
          participationDomainService.createInstantParticipationIfRecruiting(participation);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void 모집중이_아닌_분철이면_false를_반환한다() {
      // given
      Participation participation = Participation.createInstant(1L, 10L, 100L, 200L, 50_000L);
      given(participationRepository.saveInstantIfRecruiting(participation)).willReturn(false);

      // when
      boolean result =
          participationDomainService.createInstantParticipationIfRecruiting(participation);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("참여 조회 테스트")
  class GetParticipationTest {

    @Test
    void 존재하는_참여를_조회한다() {
      // given
      Participation participation = Participation.createBid(1L, 10L, 100L, 200L, 30_000L);
      given(participationRepository.findById(1L)).willReturn(Optional.of(participation));

      // when
      Participation result = participationDomainService.getParticipation(1L);

      // then
      assertThat(result).isSameAs(participation);
    }

    @Test
    void 존재하지_않는_참여를_조회하면_예외가_발생한다() {
      // given
      given(participationRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> participationDomainService.getParticipation(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("활성 참여 조회 테스트")
  class FindActiveParticipationTest {

    @Test
    void 활성_참여가_존재하면_반환한다() {
      // given
      Participation participation = Participation.createBid(1L, 10L, 100L, 200L, 30_000L);
      given(participationRepository.findActiveByBuncheolMemberIdAndParticipantId(10L, 100L))
          .willReturn(Optional.of(participation));

      // when
      Optional<Participation> result =
          participationDomainService.findActiveParticipation(10L, 100L);

      // then
      assertThat(result).isPresent();
    }

    @Test
    void 활성_참여가_없으면_빈_Optional을_반환한다() {
      // given
      given(participationRepository.findActiveByBuncheolMemberIdAndParticipantId(10L, 100L))
          .willReturn(Optional.empty());

      // when
      Optional<Participation> result =
          participationDomainService.findActiveParticipation(10L, 100L);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("즉시 구매 슬롯 확인 테스트")
  class IsInstantSlotTakenTest {

    @Test
    void 즉시_구매_슬롯이_점유되어_있으면_true를_반환한다() {
      // given
      given(participationRepository.existsActiveInstantByBuncheolMemberId(10L)).willReturn(true);

      // when
      boolean result = participationDomainService.isInstantSlotTaken(10L);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void 즉시_구매_슬롯이_비어있으면_false를_반환한다() {
      // given
      given(participationRepository.existsActiveInstantByBuncheolMemberId(10L)).willReturn(false);

      // when
      boolean result = participationDomainService.isInstantSlotTaken(10L);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("참여 상태 업데이트 테스트")
  class UpdateParticipationStatusTest {

    @Test
    void 상태_업데이트에_성공한다() {
      // given
      Participation participation = Participation.createBid(1L, 10L, 100L, 200L, 30_000L);
      given(
              participationRepository.updateStatus(
                  participation, ParticipationStatus.PAYMENT_PENDING))
          .willReturn(true);

      // when & then (예외 없이 완료)
      participationDomainService.updateParticipationStatus(
          participation, ParticipationStatus.PAYMENT_PENDING);
      then(participationRepository)
          .should()
          .updateStatus(participation, ParticipationStatus.PAYMENT_PENDING);
    }

    @Test
    void 상태_업데이트_실패시_예외가_발생한다() {
      // given
      Participation participation = Participation.createBid(1L, 10L, 100L, 200L, 30_000L);
      given(
              participationRepository.updateStatus(
                  participation, ParticipationStatus.PAYMENT_PENDING))
          .willReturn(false);

      // when & then
      assertThatThrownBy(
              () ->
                  participationDomainService.updateParticipationStatus(
                      participation, ParticipationStatus.PAYMENT_PENDING))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("제시 일괄 실패 처리 테스트")
  class FailAllOpenBidsTest {

    @Test
    void 제시_일괄_실패_처리를_위임한다() {
      // when
      participationDomainService.failAllOpenBids(10L, "즉시 구매 확정");

      // then
      then(participationRepository).should().failAllOpenBidsByBuncheolMemberId(10L, "즉시 구매 확정");
    }
  }
}
