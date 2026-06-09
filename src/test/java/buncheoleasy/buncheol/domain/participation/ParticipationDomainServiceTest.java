package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Duration;
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

  @Nested
  @DisplayName("분철 cancel cascade — 활성 참여 일괄 전이 테스트")
  class CancelActiveByBuncheolIdTest {

    @Test
    void 리포지토리에_그대로_위임하고_갱신_행_수를_반환한다() {
      Instant now = Instant.parse("2026-05-14T12:00:00Z");
      given(participationRepository.cancelActiveByBuncheolId(1L, now)).willReturn(3);

      int affected = participationDomainService.cancelActiveByBuncheolId(1L, now);

      assertThat(affected).isEqualTo(3);
      then(participationRepository).should().cancelActiveByBuncheolId(1L, now);
    }
  }

  @Nested
  @DisplayName("낙찰자 선정 테스트")
  class SelectWinnersTest {

    private static final Long BUNCHEOL_ID = 1L;

    private Participation bid(final Long memberId, final Long participantId, final long bidAmount) {
      return Participation.create(BUNCHEOL_ID, memberId, participantId, 200L, bidAmount);
    }

    @Test
    void 멤버별_최고가_1인을_낙찰하고_나머지는_차순위_후보로_ACTIVE_BID_유지한다() {
      Instant now = Instant.parse("2026-03-13T12:00:00Z");
      Participation memberA1 = bid(10L, 100L, 50_000L);
      Participation memberA2 = bid(10L, 101L, 30_000L);
      Participation memberB1 = bid(20L, 102L, 20_000L);
      // 리포지토리는 bidAmount DESC, id ASC 로 정렬해 반환한다.
      given(participationRepository.findBiddingByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(memberA1, memberA2, memberB1));

      participationDomainService.selectWinners(BUNCHEOL_ID, now);

      Instant expectedDueAt = now.plus(Duration.ofHours(24));
      assertThat(memberA1.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(memberA1.getClosedRank()).isEqualTo(1);
      assertThat(memberA1.getDueAt()).isEqualTo(expectedDueAt);

      // 2순위는 차순위 승계 후보로 ACTIVE_BID 유지 + closedRank 만 부여 (FAILED 처리하지 않음).
      assertThat(memberA2.getStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      assertThat(memberA2.getClosedRank()).isEqualTo(2);
      assertThat(memberA2.getDueAt()).isNull();
      assertThat(memberA2.getFinalizedAt()).isNull();

      assertThat(memberB1.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(memberB1.getClosedRank()).isEqualTo(1);
      assertThat(memberB1.getDueAt()).isEqualTo(expectedDueAt);
    }

    @Test
    void 단독_참여_멤버는_곧바로_낙찰된다() {
      Instant now = Instant.parse("2026-03-13T12:00:00Z");
      Participation only = bid(10L, 100L, 10_000L);
      given(participationRepository.findBiddingByBuncheolId(BUNCHEOL_ID)).willReturn(List.of(only));

      participationDomainService.selectWinners(BUNCHEOL_ID, now);

      assertThat(only.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(only.getClosedRank()).isEqualTo(1);
    }

    @Test
    void ACTIVE_BID_참여가_없으면_아무_전이도_하지_않는다() {
      Instant now = Instant.parse("2026-03-13T12:00:00Z");
      given(participationRepository.findBiddingByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      participationDomainService.selectWinners(BUNCHEOL_ID, now);

      then(participationRepository).should().findBiddingByBuncheolId(BUNCHEOL_ID);
    }
  }

  @Nested
  @DisplayName("미입금 만료 및 차순위 승계 테스트")
  class ExpireWinnerAndPromoteNextTest {

    private static final Long BUNCHEOL_MEMBER_ID = 10L;
    private static final Instant DUE_AT = Instant.parse("2026-03-13T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-13T12:00:01Z"); // dueAt 경과 직후

    @Test
    void 차순위_후보가_있으면_winner를_FAILED_처리하고_후보를_승계한다() {
      Participation winner = awaitingWinner();
      Participation candidate = activeBidCandidate(2);
      given(participationRepository.existsPaymentInProgressInSlot(BUNCHEOL_MEMBER_ID))
          .willReturn(false);
      given(participationRepository.findTopActiveBidInSlot(BUNCHEOL_MEMBER_ID))
          .willReturn(Optional.of(candidate));

      Optional<Participation> promoted =
          participationDomainService.expireWinnerAndPromoteNext(winner, NOW);

      assertThat(winner.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(winner.getFailReason()).isEqualTo("입금 기한 초과");
      assertThat(promoted).containsSame(candidate);
      assertThat(candidate.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(candidate.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(24))); // 새 24h 기한
      assertThat(candidate.getClosedRank()).isEqualTo(2); // 마감 순위 보존
    }

    @Test
    void 차순위_후보가_없으면_winner만_FAILED_로_남는다() {
      Participation winner = awaitingWinner();
      given(participationRepository.existsPaymentInProgressInSlot(BUNCHEOL_MEMBER_ID))
          .willReturn(false);
      given(participationRepository.findTopActiveBidInSlot(BUNCHEOL_MEMBER_ID))
          .willReturn(Optional.empty());

      Optional<Participation> promoted =
          participationDomainService.expireWinnerAndPromoteNext(winner, NOW);

      assertThat(winner.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(promoted).isEmpty();
    }

    @Test
    void 슬롯에_결제_진행중_참여가_있으면_중복_승계하지_않는다() {
      Participation winner = awaitingWinner();
      given(participationRepository.existsPaymentInProgressInSlot(BUNCHEOL_MEMBER_ID))
          .willReturn(true);

      Optional<Participation> promoted =
          participationDomainService.expireWinnerAndPromoteNext(winner, NOW);

      assertThat(winner.getStatus()).isEqualTo(ParticipationStatus.FAILED); // 만료는 수행됨
      assertThat(promoted).isEmpty();
      // 가드에 걸리면 차순위 후보 조회 자체를 하지 않는다.
      then(participationRepository).should(never()).findTopActiveBidInSlot(any());
    }

    private Participation awaitingWinner() {
      Participation winner = Participation.create(1L, BUNCHEOL_MEMBER_ID, 100L, 200L, 50_000L);
      setField(winner, "status", ParticipationStatus.AWAITING_PAYMENT);
      setField(winner, "dueAt", DUE_AT);
      setField(winner, "closedRank", 1);
      return winner;
    }

    private Participation activeBidCandidate(final int closedRank) {
      Participation candidate = Participation.create(1L, BUNCHEOL_MEMBER_ID, 101L, 201L, 30_000L);
      setField(candidate, "closedRank", closedRank);
      return candidate;
    }
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
