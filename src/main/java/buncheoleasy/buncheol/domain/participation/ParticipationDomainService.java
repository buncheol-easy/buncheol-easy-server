package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipationDomainService {

  // 낙찰자 입금 기한 (계좌이체 MVP). 1순위 낙찰·차순위 승계 모두 24h 를 부여한다.
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

  /** 분철의 ACTIVE_BID 참여 전체 조회. 분철 취소 시 알림 대상(취소될 참여자) 선조회에 쓴다. */
  public List<Participation> findBiddingByBuncheolId(final Long buncheolId) {
    return participationRepository.findBiddingByBuncheolId(buncheolId);
  }

  /** 입금 기한 임박 참여 조회(기한 임박 알림 스케줄러용). */
  public List<Participation> findAwaitingPaymentReminderTargets(
      final Instant now, final Instant dueBefore) {
    return participationRepository.findAwaitingPaymentReminderTargets(now, dueBefore);
  }

  /**
   * 미입금 낙찰자를 만료(FAILED)시키고 같은 멤버 슬롯의 차순위 후보를 승계한다.
   *
   * <ol>
   *   <li>{@code winner} 를 {@link Participation#expireUnpaid(Instant)} 로 FAILED 전이 (AWAITING_PAYMENT + 기한 경과만 허용)
   *   <li>슬롯에 결제 진행 중(AWAITING/PAYMENT_REPORTED/CONFIRMED) 참여가 남아 있으면 중복 결제 대상을 막기 위해 승계하지 않는다.
   *       1단계에서 winner 를 FAILED 로 바꾼 뒤 조회하므로(영속성 컨텍스트 auto-flush) 정상 흐름에선 가드가 통과한다.
   *   <li>{@code closedRank ASC} 차순위 ACTIVE_BID 후보를 새 입금 기한으로 {@link Participation#promoteToWinner(Instant)} 승계
   * </ol>
   *
   * <p>엔티티를 직접 변경하는 dirty checking 방식이므로 호출자 트랜잭션({@code @Transactional}) 안에서 실행돼야 한다.
   *
   * @return 승계된 차순위 참여. 후보가 없거나 중복 가드에 걸리면 empty (이때 winner 만 FAILED 로 남는다)
   */
  public Optional<Participation> expireWinnerAndPromoteNext(
      final Participation winner, final Instant now) {
    winner.expireUnpaid(now);

    final Long buncheolMemberId = winner.getBuncheolMemberId();
    if (participationRepository.existsPaymentInProgressInSlot(buncheolMemberId)) {
      return Optional.empty();
    }

    Optional<Participation> next =
        participationRepository.findTopActiveBidInSlot(buncheolMemberId);
    next.ifPresent(candidate -> candidate.promoteToWinner(now.plus(PAYMENT_DUE_WINDOW)));
    return next;
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
  public List<Participation> selectWinners(final Long buncheolId, final Instant now) {
    List<Participation> bids = participationRepository.findBiddingByBuncheolId(buncheolId);
    Map<Long, List<Participation>> bidsByMember =
        bids.stream().collect(Collectors.groupingBy(Participation::getBuncheolMemberId));

    Instant dueAt = now.plus(PAYMENT_DUE_WINDOW);
    List<Participation> winners = new ArrayList<>();
    for (List<Participation> memberBids : bidsByMember.values()) {
      // memberBids 는 조회 정렬(bidAmount DESC, id ASC = 높은 제시가 → 먼저 신청한 순)을 유지하므로 첫 원소가 멤버별 최고가 낙찰자다.
      for (int rankIndex = 0; rankIndex < memberBids.size(); rankIndex++) {
        Participation bid = memberBids.get(rankIndex);
        if (rankIndex == 0) {
          bid.awardAsWinner(dueAt); // closedRank=1 + AWAITING_PAYMENT + dueAt
          winners.add(bid);
        } else {
          bid.assignClosedRank(rankIndex + 1); // ACTIVE_BID 유지, 차순위 승계 후보
        }
      }
    }
    return winners;
  }
}
