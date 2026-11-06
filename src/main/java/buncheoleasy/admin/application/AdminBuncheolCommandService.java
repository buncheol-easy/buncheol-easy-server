package buncheoleasy.admin.application;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledFinalizer;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 회원 개최 분철 파기. 입금 단계가 시작된 분철은 개최자가 혼자 접지 못하고(BCH-126), 운영자가 실제 입금과 환불 계획을 확인한 뒤
 * 여기서 접는다. 입금 확인 건수와 무관하게 분철을 숨기고(HOST_CANCELLED) 입금 확인된 참여까지 전원 취소하며 참여자에게 「분철 취소 안내」를
 * 보낸다. 환불은 개최자가 하고 운영자가 확인한다(기능 없음).
 *
 * <p>운송장이 하나라도 있으면 발송이 시작된 것이라 상태와 무관하게 BCH-130 으로 거절한다. 옛 서버는 진행 확정 전에도 운송장을 받아서
 * 입금 수집 중 분철에도 운송장이 있을 수 있다. 운송장 전이면 진행 확정 뒤라도 파기하고, 진행 확정 때 만들어진 배송은 취소 연쇄가 지운다.
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

  /**
   * 기본 격리로 둔다 — 첫 문장이 분철 행 잠금 조회라 잠금 앞에 일반 조회가 없고, 잠금 뒤 판단은 잠금 읽기와 CAS 로만 한다. 앞에 일반
   * 조회를 더하게 되면 묶음 입금 확인처럼 READ COMMITTED 로 바꾼다.
   */
  @Transactional
  public void cancel(final Long buncheolId) {
    final Instant now = Instant.now(clock);
    // 분철 행이 첫 잠금이다 — 진행 확정·묶음 입금 확인과 분철 행에서 한 줄로 선다. 상태로 가지를 골라야 해서 CAS 앞에서 잠가 읽는다.
    Buncheol buncheol = buncheolDomainService.getBuncheolForUpdate(buncheolId);
    BuncheolStatus before = buncheol.getStatus();
    if (buncheol.isStaff()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    switch (before) {
      case PAYMENT_COLLECTING, CONFIRMED -> requireNoTracking(buncheolId);
      default -> throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    if (!buncheolDomainService.staffCancelIfStatus(buncheolId, before, now)) {
      // 분철 행을 잠근 채 상태를 봤으므로 여기 오면 안 된다 — 롤백한다.
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
    log.info(
        "운영진 파기 - buncheolId={}, 파기 전 상태 {}, 취소 참여 {}건", buncheolId, before, cancelledCount);
  }

  /**
   * 운송장 전 = 살아 있는 참여의 배송이 모두 SNAPSHOTTED(배송이 없어도 운송장 전). 입금 수집 중·진행 확정 모두 같은 검사다. 운송장
   * 번호를 쓰는 길은 상태를 SHIPPING 으로 함께 바꾸는 CAS 하나라 상태만 보면 된다. 배송 행을 잠가 읽으므로 운송장 등록과 겹치면 배송
   * 행에서 한쪽만 반영된다 — 등록이 먼저 커밋되면 여기서 SHIPPING 을 보고, 파기가 먼저 커밋되면 등록 CAS 가 지워진 행을 보고 실패한다.
   */
  private void requireNoTracking(final Long buncheolId) {
    List<Long> activeIds =
        participationDomainService.findActiveByBuncheolIdForUpdate(buncheolId).stream()
            .map(Participation::getId)
            .toList();
    boolean hasTracking =
        deliveryDomainService.findAllByParticipationIdsForUpdate(activeIds).stream()
            .anyMatch(delivery -> delivery.getStatus() != DeliveryStatus.SNAPSHOTTED);
    if (hasTracking) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_HAS_TRACKING);
    }
  }
}
