package buncheoleasy.admin.application;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledFinalizer;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 회원 개최 분철 파기. 입금 단계가 시작된 분철은 개최자가 혼자 접지 못하고(BCH-126), 운영자가 실제 입금과 환불 계획을 확인한 뒤
 * 여기서 접는다. 입금 확인 건수와 무관하게 분철을 숨기고(HOST_CANCELLED) 입금 확인된 참여까지 전원 취소하며 참여자에게 「분철 취소 안내」를
 * 보낸다. 환불은 개최자가 하고 운영자가 확인한다(기능 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBuncheolCommandService {

  private final BuncheolDomainService buncheolDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final DeliveryDomainService deliveryDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void cancel(final Long buncheolId) {
    final Instant now = Instant.now(clock);
    // 이 CAS 가 트랜잭션의 첫 잠금(분철 행)이다 — 진행 확정·묶음 입금 확인과 분철 행에서 한 줄로 선다.
    if (!buncheolDomainService.staffCancelIfCollecting(buncheolId, now)) {
      // 판정은 위 CAS 로 끝났고, 여기서 읽는 것은 어느 안내를 줄지뿐이다 — 없는 분철이면 404.
      buncheolDomainService.getBuncheol(buncheolId);
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    int cancelledCount =
        new BuncheolCancelledFinalizer(
                participationDomainService,
                participationBundleDomainService,
                deliveryDomainService,
                eventPublisher)
            .finalizeCancelled(buncheolId, now, BuncheolCancelReason.HOST_CANCELLED);
    // 되돌릴 수 없는 명령이라 참여자 문의 때 첫 근거로 남긴다.
    log.info("운영진 파기 - buncheolId={}, 취소 참여 {}건", buncheolId, cancelledCount);
  }
}
