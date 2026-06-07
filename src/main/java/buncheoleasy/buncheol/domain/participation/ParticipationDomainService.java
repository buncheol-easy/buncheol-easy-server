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

  // 낙찰자 입금 기한 (계좌이체 MVP). 1순위·차순위 모두 24h. 미입금 만료/차순위 이양은 관리자 수동 API(별도 작업).
  private static final Duration PAYMENT_DUE_WINDOW = Duration.ofHours(24);

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
   * 마감된 분철의 낙찰자를 선정한다. ACTIVE_BID 참여를 멤버 슬롯별로 묶어 제시가 순위(closedRank)를 매기고, 멤버별 1순위만 {@code
   * AWAITING_PAYMENT}(낙찰, 입금 기한 {@code dueAt} 부여)로 전이한다. 2순위 이하는 차순위 승계 후보로 남겨야 하므로 {@code ACTIVE_BID}
   * 를 유지하고 closedRank 만 부여한다 (미입금 시 차순위 이양은 관리자 수동 API에서 처리).
   *
   * <p>관리 엔티티를 직접 변경하는 dirty checking 방식이므로 호출자 트랜잭션({@code @Transactional}) 안에서 실행돼야 한다. 2순위 이하가
   * ACTIVE_BID 로 남으므로 이 메서드 자체는 멱등하지 않다 — 정확히 마감 1회만 실행되도록 호출 측의 Buncheol RECRUITING→CLOSED
   * 가드(Buncheol.close)에 의존한다 (수동 마감·자동 마감 스케줄러가 공유).
   */
  public void selectWinners(final Long buncheolId, final Instant now) {
    List<Participation> bids = participationRepository.findBiddingByBuncheolId(buncheolId);
    Map<Long, List<Participation>> bidsByMember =
        bids.stream().collect(Collectors.groupingBy(Participation::getBuncheolMemberId));

    Instant dueAt = now.plus(PAYMENT_DUE_WINDOW);
    for (List<Participation> memberBids : bidsByMember.values()) {
      // memberBids 는 조회 정렬(bidAmount DESC, id ASC = 높은 제시가 → 먼저 신청한 순)을 유지하므로 첫 원소가 멤버별 최고가 낙찰자다.
      for (int rankIndex = 0; rankIndex < memberBids.size(); rankIndex++) {
        Participation bid = memberBids.get(rankIndex);
        if (rankIndex == 0) {
          bid.awardAsWinner(dueAt); // closedRank=1 + AWAITING_PAYMENT + dueAt
        } else {
          bid.assignClosedRank(rankIndex + 1); // ACTIVE_BID 유지, 차순위 승계 후보
        }
      }
    }
  }
}
