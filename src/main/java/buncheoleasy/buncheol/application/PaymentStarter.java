package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 회원 개최 분철의 입금 단계 시작 (RECRUITING → PAYMENT_COLLECTING). 마감 스케줄러, 마지막 열린 자리를 채운 신청, 자리 빼기 세 입구가
 * 이것 하나를 쓴다 — 개최자가 누르는 단계는 없다.
 *
 * <p>🔴 호출자가 분철 행을 잠근 트랜잭션 안에서만 부른다({@link BuncheolDomainService#getBuncheolForUpdate}). 넘기는 분철은 그
 * 잠금으로 읽은 최신 값이어야 한다(최소 인원). 판정은 잠금 읽기로만 한다 — 열린 자리는 공유 잠금, 살아 있는 참여는 바로 아래에서 바꿀
 * 행이라 배타 잠금이다. 잠금 전 스냅샷으로 세면 기다리는 사이 커밋된 신청·취소·자리 빼기를 놓친다.
 *
 * <p>칸은 옛 성사 확정과 같은 CAS 로 채운다(상태·입금 기한·{@code finalized_at}·계좌 스냅샷). 옛 서버가 연 입금 수집 중 분철과 모양이
 * 같아야 배포 중에 두 서버가 같은 행을 읽는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentStarter {

  /**
   * 입금 기한 길이(24시간). 입금 단계 시작 때 분철 전체에, 진행 확정 뒤 추가 신청 수락 때 그 묶음에 쓴다. 지나도 서버가 처리하지 않는다 —
   * 입금 단계에서는 개최자의 진행 확정이, 추가 묶음에서는 「제외」가 이 시각부터 열린다. 알림톡 본문의 "24시간"과 같은 값이다.
   */
  public static final Duration PAYMENT_TIME_LIMIT = Duration.ofHours(24);

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final UserDomainService userDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 열린 자리(닫힌 자리 제외)가 모두 신청됐고 최소 인원 이상이면 입금 단계를 시작한다. 신청과 자리 빼기가 부른다.
   *
   * @return 이 호출이 입금 단계를 시작했으면 true
   */
  public boolean startIfFull(final Buncheol buncheol, final Instant now) {
    long openSlotCount =
        buncheolMemberDomainService.findAllByBuncheolIdForShare(buncheol.getId()).stream()
            .filter(slot -> !slot.isClosed())
            .count();
    List<Participation> active =
        participationDomainService.findActiveByBuncheolIdForUpdate(buncheol.getId());
    if (active.size() < openSlotCount || active.size() < buncheol.getMinHeadcount()) {
      return false;
    }
    return start(buncheol, active, now);
  }

  /**
   * 마감이 지난 분철에서 신청 자리가 최소 인원 이상이면 입금 단계를 시작한다. 마감 스케줄러가 부르며, false 면 호출자가 미달 취소한다.
   *
   * @return 이 호출이 입금 단계를 시작했으면 true (최소 인원 미달이거나 이미 모집 중이 아니면 false)
   */
  public boolean startAtDeadline(final Buncheol buncheol, final Instant now) {
    List<Participation> active =
        participationDomainService.findActiveByBuncheolIdForUpdate(buncheol.getId());
    if (active.size() < buncheol.getMinHeadcount()) {
      return false;
    }
    return start(buncheol, active, now);
  }

  private boolean start(
      final Buncheol buncheol, final List<Participation> active, final Instant now) {
    // 정산 계좌는 지우는 API 가 없어(교체만) 개최 때 등록한 계좌가 남아 있다. 비었다면 데이터 이상이라 시작하지 않고 롤백한다.
    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    if (hostAccount == null) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
    Instant paymentDueAt = now.plus(PAYMENT_TIME_LIMIT);
    if (!buncheolDomainService.startCollecting(buncheol.getId(), paymentDueAt, hostAccount, now)) {
      return false;
    }

    List<Long> appliedIds =
        active.stream()
            .filter(participation -> participation.getStatus() == ParticipationStatus.APPLIED)
            .map(Participation::getId)
            .toList();
    int moved =
        participationDomainService.startPaymentCollecting(buncheol.getId(), paymentDueAt, now);
    if (moved != appliedIds.size()) {
      // 참여 행을 잠근 채 셌으므로 여기 오면 내부 불변식 위반이다 — 롤백해 분철만 넘어간 상태를 남기지 않는다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    // 신청 구간의 묶음은 기한 없이 열려 있다 — 참여와 같은 기한을 묶음에도 채운다(묶음 기한이 정본).
    participationBundleDomainService.assignDueAtByBuncheolId(buncheol.getId(), paymentDueAt, now);

    eventPublisher.publishEvent(new PaymentStartedEvent(buncheol.getId(), appliedIds));
    return true;
  }
}
