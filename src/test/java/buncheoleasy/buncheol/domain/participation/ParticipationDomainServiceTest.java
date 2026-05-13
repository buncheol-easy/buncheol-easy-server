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

  private static Participation newParticipation() {
    return Participation.create(1L, 10L, 100L, 200L, 30_000L);
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
      given(participationRepository.findById(1L)).willReturn(Optional.of(participation));

      Participation result = participationDomainService.getParticipation(1L);

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
  @DisplayName("활성 참여 조회 테스트")
  class FindActiveParticipationTest {

    @Test
    void 활성_참여가_존재하면_반환한다() {
      Participation participation = newParticipation();
      given(participationRepository.findActiveByBuncheolMemberIdAndParticipantId(10L, 100L))
          .willReturn(Optional.of(participation));

      Optional<Participation> result =
          participationDomainService.findActiveParticipation(10L, 100L);

      assertThat(result).isPresent();
    }

    @Test
    void 활성_참여가_없으면_빈_Optional을_반환한다() {
      given(participationRepository.findActiveByBuncheolMemberIdAndParticipantId(10L, 100L))
          .willReturn(Optional.empty());

      Optional<Participation> result =
          participationDomainService.findActiveParticipation(10L, 100L);

      assertThat(result).isEmpty();
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
  @DisplayName("참여 상태 업데이트 테스트")
  class UpdateParticipationStatusTest {

    @Test
    void 상태_업데이트에_성공한다() {
      Participation participation = newParticipation();
      given(participationRepository.updateStatus(participation, ParticipationStatus.ACTIVE_BID))
          .willReturn(true);

      participationDomainService.updateParticipationStatus(
          participation, ParticipationStatus.ACTIVE_BID);

      then(participationRepository)
          .should()
          .updateStatus(participation, ParticipationStatus.ACTIVE_BID);
    }

    @Test
    void 상태_업데이트_실패시_예외가_발생한다() {
      Participation participation = newParticipation();
      given(participationRepository.updateStatus(participation, ParticipationStatus.ACTIVE_BID))
          .willReturn(false);

      assertThatThrownBy(
              () ->
                  participationDomainService.updateParticipationStatus(
                      participation, ParticipationStatus.ACTIVE_BID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }
}
