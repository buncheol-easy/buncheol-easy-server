package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.BundleReleasability;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 묶음 단위 개최자 조작. 묶음은 현실의 돈 단위(이체 1회·배송비 1회·택배 1개)이므로 조작도 그 단위로 한다. */
@Service
@RequiredArgsConstructor
public class ParticipationBundleService {

  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ParticipationRepository participationRepository;
  private final BuncheolDomainService buncheolDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 개최자가 입금 기한이 지난 묶음을 「제외」한다 (docs/70 결정 8).
   *
   * <p>C2C 는 자동 만료가 없으므로(결정 9) <b>이것이 미입금자를 빼는 유일한 출구</b>다. 없으면 분철이 「입금 수집중」에
   * 영구 정체한다.
   *
   * <p>판정({@link BundleReleasability})으로 <b>사유를 가려 에러코드로 바꾸고</b>, 실제 차단은 CAS 가 한다 —
   * 판정과 실행 사이에 마지막 슬롯이 입금확인되거나 개최자가 반려로 기한을 미는 창이 있기 때문이다. 판정을 조회 응답과
   * 공유해 "버튼은 있는데 누르면 409" 가 생기지 않게 한다.
   */
  @Transactional
  public List<Long> release(final Long hostId, final Long bundleId) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle =
        participationBundleDomainService
            .findById(bundleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNDLE_NOT_FOUND));
    Buncheol buncheol = buncheolDomainService.getBuncheol(bundle.getBuncheolId());
    buncheol.validateOwner(hostId);
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    List<Participation> slots = participationRepository.findAllByBundleIds(List.of(bundleId));
    requireReleasable(BundleReleasability.of(bundle, slots, now));

    // 실제로 뺄 슬롯을 CAS 전에 붙잡아 둔다 — CAS 는 영향 행 수만 돌려주고, 알림은 어떤 슬롯이 빠졌는지 알아야 한다.
    List<Long> targets =
        slots.stream()
            .filter(p -> ParticipationStatus.releasableStatuses().contains(p.getStatus()))
            .map(Participation::getId)
            .toList();

    int released = participationRepository.releaseBundleIfDue(bundleId, now);
    if (released == 0) {
      // 판정을 통과했는데 0행이면 그 사이 상태가 바뀐 것이다 — 화면을 새로 고치게 한다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participationBundleDomainService.closeIfEmpty(bundleId, now);

    eventPublisher.publishEvent(new BundleReleasedEvent(bundleId, targets));
    return targets;
  }

  /** 판정 → 에러코드. switch <b>식</b>이라 사유가 늘면 컴파일 에러로 잡힌다 (fail-open 방지). */
  private static void requireReleasable(final BundleReleasability releasability) {
    ErrorCode errorCode =
        switch (releasability) {
          case RELEASABLE -> null;
          case RECRUITING -> ErrorCode.BUNDLE_RELEASE_RECRUITING;
          case BEFORE_DUE -> ErrorCode.BUNDLE_RELEASE_BEFORE_DUE;
          case HAS_CONFIRMED -> ErrorCode.BUNDLE_RELEASE_HAS_CONFIRMED;
          case ALREADY_CLOSED -> ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID;
        };
    if (errorCode != null) {
      throw new BusinessException(errorCode);
    }
  }
}
