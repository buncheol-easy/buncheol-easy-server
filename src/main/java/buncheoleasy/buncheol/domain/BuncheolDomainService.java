package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolDomainService {

  private final BuncheolRepository buncheolRepository;
  private final Clock clock;

  public Buncheol createBuncheol(final Long hostId, final BuncheolParams params) {
    return buncheolRepository.save(Buncheol.create(hostId, params, Instant.now(clock)));
  }

  public Buncheol getBuncheol(final Long id) {
    return buncheolRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }

  // C2C 오픈으로 can_host 게이트가 사라진 자리의 남용 방지(무제한 개최·건당 이미지 5장 업로드) —
  // 일반 유저의 활성(모집중·입금 수집중) 개최 수 상한. 운영진(can_host)은 이벤트 운영을 위해 적용하지 않는다.
  private static final int MAX_ACTIVE_HOSTED = 5;

  public void validateActiveHostedLimit(final Long hostId) {
    if (isActiveHostedLimitExceeded(hostId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED);
    }
  }

  /** 던지지 않는 상한 판정 — 개최 폼 진입 전 사전 조회용(docs/53 Q-07). 제출 게이트와 같은 카운트를 공유한다. */
  public boolean isActiveHostedLimitExceeded(final Long hostId) {
    return buncheolRepository.countActiveByHostId(hostId) >= MAX_ACTIVE_HOSTED;
  }

  public void updateBuncheolContent(
      final Buncheol buncheol, final String title, final String description) {
    buncheol.updateContent(title, description);
  }

  public void updateBuncheolOpenChatUrl(final Buncheol buncheol, final String openChatUrl) {
    buncheol.updateOpenChatUrl(openChatUrl);
  }

  /**
   * 마감 판정(진행확정/취소)을 단일 CAS 로 원자 전이한다. 입금확인 인원이 최소 인원 이상이면 CONFIRMED, 미만이면 CANCELLED. 카운트·비교·전이를 한
   * UPDATE 로 묶어, 카운트 조회와 전이 사이에 입금확인이 커밋돼 발생하는 오판(stale count)을 방지한다.
   *
   * @return 전이에 성공하면 true, 이미 마감 판정됐거나 RECRUITING 이 아니면 false (실제 전이 상태는 호출 측이 재조회로 판별)
   */
  public boolean finalizeExpiredByConfirmedHeadcount(final Long buncheolId, final Instant now) {
    return buncheolRepository.finalizeExpiredByConfirmedHeadcount(buncheolId, now) > 0;
  }

  /**
   * 전 슬롯 입금확인 시 조기 진행확정 CAS (RECRUITING → CONFIRMED). 입금확인 수·최소인원 판정을 UPDATE WHERE 서브쿼리로 원자화해,
   * 마지막 슬롯을 동시에 입금확인하는 경합에서도 조기 확정을 놓치지 않는다. 호출 측 {@code @Transactional} 필수.
   *
   * @return 조기 확정에 성공하면 true, 아직 매진 아님/최소인원 미달/이미 마감이면 false
   */
  public boolean confirmIfAllSlotsConfirmed(
      final Long buncheolId, final long totalSlots, final Instant now) {
    return buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, totalSlots, now) > 0;
  }

  /**
   * 호스트의 분철 취소 (RECRUITING/CANCELLED → HOST_CANCELLED CAS). 진행확정(CONFIRMED)·이미 개최자 취소된 분철은 상태 위반으로
   * 막는다. 목록·상세에서 숨겨지며(하드 삭제 대신 소프트 숨김), 활성 참여 cascade 취소·알림은 호출 측이 반환된 직전 상태로 판단해 처리한다.
   *
   * <p>상태별 CAS 를 순차 시도해 마감 스케줄러와 경합해도 성공한 시도로 직전 상태가 확정된다 — 허용 상태 IN 단일 CAS 로는 어느 상태에서 전이됐는지 알 수 없어,
   * 자동취소 직후 케스케이드를 중복 실행(취소 알림 재발송)할 수 있다.
   *
   * @return 전이 직전 상태 (RECRUITING 이면 케스케이드 필요, CANCELLED 면 자동취소 시 이미 완료됨)
   */
  public BuncheolStatus cancelBuncheol(final Long buncheolId, final Instant now) {
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.RECRUITING;
    }
    // C2C 입금 수집중 취소 (docs/46 §7.1-6 — 기한 경과 후 개최자 선택지). LEGACY 는 이 상태가 없어 영향 없다.
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.PAYMENT_COLLECTING, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.PAYMENT_COLLECTING;
    }
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.CANCELLED, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.CANCELLED;
    }
    throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
  }

  /** C2C 확정 유예 경과 미성사 취소 CAS (RECRUITING → CANCELLED — docs/46 §7.1-5). 호출 측 {@code @Transactional} 필수. */
  public boolean cancelUnconfirmedC2c(final Long buncheolId, final Instant now) {
    return buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.RECRUITING, BuncheolStatus.CANCELLED, now)
        > 0;
  }

  /** 호스트에게 아직 끝나지 않은 분철이 있는지 (회원탈퇴 가드용). 판정 기준은 포트 javadoc 참고. */
  public boolean hasUnfinishedBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsUnfinishedByHostId(hostId);
  }

  // --- C2C 플로우 전이 (docs/46 §4) ---

  /**
   * C2C 성사 확정 CAS (RECRUITING → PAYMENT_COLLECTING). 일괄 입금 기한과 확정 시점 개최자 계좌 스냅샷을 함께 기록한다.
   * 호출 측 {@code @Transactional} 필수.
   *
   * @return 전이에 성공하면 true (false 면 RECRUITING 이 아니거나 C2C 분철이 아님)
   */
  public boolean startCollecting(
      final Long buncheolId,
      final Instant paymentDueAt,
      final BankAccount hostAccount,
      final Instant now) {
    return buncheolRepository.startCollectingIfRecruiting(
            buncheolId,
            paymentDueAt,
            hostAccount.bank(),
            hostAccount.account(),
            hostAccount.holder(),
            now)
        > 0;
  }

  /**
   * C2C 전원 입금확인 시 진행확정 CAS (PAYMENT_COLLECTING → CONFIRMED). 미확정 활성 참여가 남아 있으면 전이하지 않는다.
   * 호출 측 {@code @Transactional} 필수.
   */
  public boolean confirmIfAllCollected(final Long buncheolId, final Instant now) {
    return buncheolRepository.confirmIfAllCollected(buncheolId, now) > 0;
  }

  /**
   * C2C 데드엔드 정리 CAS — 입금 수집중인데 활성 참여가 하나도 남지 않았으면(확정 0건) 미성사 취소한다 (docs/46 §7.1-6 보완).
   * 확정 참여가 있으면 전이하지 않고 개최자 선택(부분 확정/취소)을 기다린다. 호출 측 {@code @Transactional} 필수.
   */
  public boolean cancelCollectingIfEmpty(final Long buncheolId, final Instant now) {
    return buncheolRepository.cancelIfCollectingAndEmpty(buncheolId, now) > 0;
  }

  /** C2C 참여 생성 직렬화용 잠금 조회 — 포트 javadoc 참고. 호출 측 {@code @Transactional} 필수. */
  public Buncheol getBuncheolForUpdate(final Long id) {
    return buncheolRepository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }
}
