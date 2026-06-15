package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolAutoCloseService {

  // 한 폴링 주기에 처리할 분철 수 상한. 마감이 몰려도 트랜잭션·조회 부하를 제한하고, 남은 분철은 다음 주기에 처리한다.
  private static final int BATCH_SIZE = 100;

  private final BuncheolRepository buncheolRepository;
  private final ParticipationDomainService participationDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /** {@code now} 기준 deadline 이 지난 RECRUITING 분철 id 를 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Long> findExpiredBuncheolIds(final Instant now) {
    return buncheolRepository.findRecruitingIdsPastDeadline(now, BATCH_SIZE);
  }

  /**
   * 단일 분철을 마감하고 낙찰자를 선정한다. {@code RECRUITING → CLOSED} CAS UPDATE 로 선점에 성공한 인스턴스만 후속 처리를 수행해 다중
   * 인스턴스 환경의 중복 마감을 막는다. 마감과 낙찰 선정을 한 트랜잭션으로 묶어, 도중 실패 시 둘 다 롤백되어 다음 주기에 재시도된다.
   *
   * @return 이 호출이 마감을 수행했으면 {@code true}, 이미 마감됐거나 RECRUITING 이 아니면 {@code false}
   */
  @Transactional
  public boolean closeExpired(final Long buncheolId, final Instant now) {
    int closed = buncheolRepository.closeIfRecruiting(buncheolId, now);
    if (closed == 0) {
      // 다른 인스턴스가 이미 마감했거나 그 사이 상태가 RECRUITING 이 아니게 됨.
      return false;
    }
    List<Participation> winners = participationDomainService.selectWinners(buncheolId, now);
    winners.forEach(winner -> eventPublisher.publishEvent(new ParticipationWonEvent(winner.getId())));
    return true;
  }
}
