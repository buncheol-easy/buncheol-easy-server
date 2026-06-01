package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipationDomainService {

  // 낙찰자 결제 기한. 현재는 정보성으로만 사용한다(미결제 시 차순위 이양/타임아웃은 별도 작업).
  private static final Duration PAYMENT_DUE_WINDOW = Duration.ofHours(48);

  private final ParticipationRepository participationRepository;

  public boolean createParticipationIfRecruiting(final Participation participation) {
    return participationRepository.saveIfRecruiting(participation);
  }

  public Participation getParticipation(final Long id) {
    return participationRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));
  }

  public Optional<Participation> findActiveParticipation(
      final Long buncheolMemberId, final Long participantId) {
    return participationRepository.findActiveByBuncheolMemberIdAndParticipantId(
        buncheolMemberId, participantId);
  }

  public void updateParticipationStatus(
      final Participation participation, final ParticipationStatus expectedStatus) {
    boolean updated = participationRepository.updateStatus(participation, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  public boolean hasActiveParticipationBy(final Long participantId) {
    return participationRepository.existsActiveByParticipantId(participantId);
  }

  public int cancelActiveByBuncheolId(final Long buncheolId, final Instant now) {
    return participationRepository.cancelActiveByBuncheolId(buncheolId, now);
  }

  /**
   * 마감된 분철의 낙찰자를 선정한다. ACTIVE_BID 참여를 멤버 슬롯별로 묶어 제시가 순위를 매기고, 멤버별 1순위는 {@code AWAITING_PAYMENT}(낙찰,
   * 결제 기한 {@code dueAt} 부여), 그 외는 {@code FAILED}(낙찰 실패) 로 전이한다.
   *
   * <p>관리 엔티티를 직접 변경하는 dirty checking 방식이므로 호출자 트랜잭션({@code @Transactional}) 안에서 실행돼야 한다. ACTIVE_BID
   * 만 대상으로 하므로 재실행 시 대상이 비어 no-op 이 되어 멱등하다 (수동 마감·자동 마감 스케줄러가 공유).
   */
  public void selectWinners(final Long buncheolId, final Instant now) {
    List<Participation> bids = participationRepository.findBiddingByBuncheolId(buncheolId);
    Map<Long, List<Participation>> bidsByMember =
        bids.stream().collect(Collectors.groupingBy(Participation::getBuncheolMemberId));

    Instant dueAt = now.plus(PAYMENT_DUE_WINDOW);
    for (List<Participation> memberBids : bidsByMember.values()) {
      // memberBids 는 조회 정렬(bidAmount DESC, id ASC)을 그대로 유지하므로 첫 원소가 멤버별 최고가 낙찰자다.
      for (int rankIndex = 0; rankIndex < memberBids.size(); rankIndex++) {
        Participation bid = memberBids.get(rankIndex);
        if (rankIndex == 0) {
          bid.awardAsWinner(dueAt);
        } else {
          bid.markNotSelected(rankIndex + 1, now);
        }
      }
    }
  }
}
