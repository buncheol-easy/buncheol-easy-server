package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 입금 만료(dueAt 도과) 참여를 CANCELLED(PAYMENT_TIMEOUT) 로 전이한다. 건별 독립 트랜잭션이라 한 건 실패가 나머지를 막지 않는다. */
@Service
@RequiredArgsConstructor
public class ParticipationPaymentExpiryService {

  // 한 폴링 주기에 처리할 참여 수 상한. 만료가 몰려도 부하를 제한하고, 남은 건은 다음 주기에 처리한다.
  private static final int BATCH_SIZE = 200;

  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /** {@code now} 기준 입금 기한이 지난 AWAITING_PAYMENT 참여를 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Participation> findOverdueTargets(final Instant now) {
    return participationDomainService.findOverduePaymentTargets(now, BATCH_SIZE);
  }

  /**
   * 단일 참여의 입금 만료 처리 (AWAITING_PAYMENT + 기한 경과일 때만 CANCELLED CAS). 인자는 {@link #findOverdueTargets}
   * 가 돌려준 대상 객체다 — 묶음 종료 판정에 그 행의 {@code bundleId} 가 필요하다. 멱등하며, 그 사이 입금확인/자발취소된 참여는 CAS 에
   * 막혀 false 를 돌려준다. 실제로 만료시킨 경우에만 참여자에게 자동취소 알림 이벤트를 발행한다(커밋 후 발송).
   */
  @Transactional
  public boolean expire(final Participation participation, final Instant now) {
    Long participationId = participation.getId();
    boolean expired = participationDomainService.expirePayment(participationId, now);
    if (expired) {
      // 만료로 마지막 슬롯이 빠졌으면 묶음도 닫는다 (docs/80 ④ 보강 3). 대상 객체는 스케줄러가 이미 조회해
      // 넘겨주므로 여기서 다시 읽지 않는다 — bundle_id 는 NULL→설정 한 방향뿐이라 스냅샷이어도 안전하다.
      participationBundleDomainService.closeIfEmpty(participation.getBundleId(), now);
      eventPublisher.publishEvent(new PaymentExpiredEvent(participationId));
    }
    return expired;
  }
}
