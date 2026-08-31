package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.BundleReleasability;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    // 🔴 <b>잠금 조회로 판정한다.</b> 확정 슬롯 검사를 CAS 서브쿼리에 넣을 수 없다 — MySQL 은 UPDATE 대상
    // 테이블을 서브쿼리 FROM 에서 참조하는 것을 금지한다(error 1093). H2 는 허용해서 테스트가 전부 통과하고
    // staging 에서야 500 으로 드러났다. 락이 그 원자성을 대신한다 — 입금확인 CAS 가 이 락에 막힌다.
    requireReleasable(
        BundleReleasability.of(
            bundle, participationRepository.findAllByBundleIdForUpdate(bundleId), now));

    int released = participationRepository.releaseBundleIfDue(bundleId, now);
    if (released == 0) {
      // 판정을 통과했는데 0행이면 그 사이 상태가 바뀐 것이다 — 화면을 새로 고치게 한다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participationBundleDomainService.closeIfEmpty(bundleId, now);

    // 🔴 <b>CAS 이후에</b> 실제로 취소된 슬롯을 뽑는다. CAS 전 스냅샷으로 계산하면 REPEATABLE READ 라,
    // 그 사이 참여자가 자발 취소한 슬롯이 우리 눈에는 아직 활성으로 보여 목록에 들어간다 — 정작 CAS 의
    // current read 는 이미 취소된 그 행을 건드리지 않는다. 그러면 <b>이 응답이 존재하는 이유인 사후 대조가
    // 오히려 틀린 답을 내고</b>, 알림톡이 자기가 직접 취소한 슬롯을 "기한이 지나 취소되었어요" 로 안내한다.
    //
    // CAS 가 cancelReason·cancelledAt 을 같은 now 로 박아 주므로 이 두 값으로 정확히 걸러진다.
    // @Modifying(clearAutomatically = true) 라 재조회가 갱신값을 본다.
    List<Long> releasedIds =
        participationRepository.findAllByBundleIds(List.of(bundleId)).stream()
            .filter(p -> p.getCancelReason() == ParticipationCancelReason.HOST_RELEASED)
            .filter(p -> writtenAt(p.getCancelledAt(), now))
            .map(Participation::getId)
            .toList();

    eventPublisher.publishEvent(new BundleReleasedEvent(bundleId, releasedIds));
    return releasedIds;
  }

  /**
   * 이 트랜잭션의 CAS 가 쓴 시각인지 판정한다.
   *
   * <p>🔴 <b>{@code equals} 로 비교하면 안 된다.</b> {@code cancelled_at}·{@code payment_sent_at} 은 초 단위
   * {@code DATETIME}(precision 0) 이라 나노초를 가진 {@code Instant} 와 <b>영원히 같지 않다</b>. staging 에서
   * 「제외」가 성공했는데 응답의 취소 목록이 비어 나온 것이 이 때문이다 — H2 는 정밀도를 보존해 테스트가
   * 통과했다.
   */
  private static boolean writtenAt(final Instant stored, final Instant now) {
    return stored != null && !stored.isBefore(now.truncatedTo(ChronoUnit.SECONDS));
  }

  /**
   * 참여자가 묶음을 한 번에 「보냈어요」로 표시한다 (docs/71 §1-2).
   *
   * <p>묶음은 <b>이체 1회</b>의 단위다. 슬롯마다 누르게 하면 ① 참여자가 한 번 보낸 돈을 여러 번 신고하게 되고
   * ② 중간에 멈추면 같은 묶음의 슬롯 상태가 갈린다 — 「제외」·입금확인이 전부 "묶음 안 슬롯은 상태가 갈리지
   * 않는다" 는 전제 위에 서 있으므로 그 전제를 여기서 지켜야 한다.
   *
   * <p><b>기한 검사가 없다.</b> 기한이 지난 뒤에도 마킹은 가능해야 한다 — 늦게 보낸 사람도 보냈다는 사실을
   * 남길 수 있어야 개최자가 확인하고, 그게 「제외」가 기한을 기다리는 이유이기도 하다.
   *
   * <p>멱등하다. 이미 전부 마킹됐으면 이벤트 없이 조용히 끝난다 — 더블탭으로 개최자 알림이 중복되면 안 된다.
   */
  @Transactional
  public List<Long> markPaymentSent(final Long participantId, final Long bundleId) {
    final Instant now = Instant.now(clock);
    ParticipationBundle bundle =
        participationBundleDomainService
            .findById(bundleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNDLE_NOT_FOUND));
    // 🔴 묶음 id 는 AUTO_INCREMENT 라 추측 가능하다 — 소유자 검증이 필수다.
    if (!participantId.equals(bundle.getParticipantId())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
    Buncheol buncheol = buncheolDomainService.getBuncheol(bundle.getBuncheolId());
    if (!buncheol.isC2c()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);
    }

    int marked = participationRepository.markBundlePaymentSent(bundleId, now);

    // 🔴 <b>잠금 조회로 다시 읽는다.</b> 평범한 SELECT 는 REPEATABLE READ 라 트랜잭션 첫 읽기 시점의 스냅샷을
    // 본다 — 동시 더블탭에서 A 가 먼저 커밋하면 B 의 UPDATE 는 current read 로 0행이 되는데, 이어지는 일반
    // 조회는 <b>아직 마킹 전으로 보여</b> "마킹할 게 없다" 며 409 를 낸다. 이 기능이 막으려던 더블탭이 정확히
    // 그 경합이다.
    List<Participation> slots = participationRepository.findAllByBundleIdForUpdate(bundleId);
    List<Long> sentIds =
        slots.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.PAYMENT_SENT)
            .map(Participation::getId)
            .toList();

    if (marked == 0) {
      // 멱등: 이미 마킹된 슬롯이 있으면 성공으로 끝낸다(더블탭). 하나도 없으면 상태 위반이다 — 개최자가
      // 이미 전부 입금확인한 뒤 누른 경우도 여기 해당한다.
      if (sentIds.isEmpty()) {
        throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_SENT_NOT_ALLOWED);
      }
      return sentIds;
    }

    // 응답은 <b>이번 호출이</b> 마킹한 슬롯이다 — 화면이 사후 대조하는 대상이 그것이다.
    List<Long> markedIds =
        slots.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.PAYMENT_SENT)
            .filter(p -> writtenAt(p.getPaymentSentAt(), now))
            .map(Participation::getId)
            .toList();
    // 알림은 <b>묶음 전체</b>의 마킹분으로 만든다 — 부분 마킹에서 금액이 축소되면 통장 대조가 어긋난다.
    eventPublisher.publishEvent(new BundlePaymentSentEvent(bundleId, markedIds, sentIds));
    return markedIds;
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
