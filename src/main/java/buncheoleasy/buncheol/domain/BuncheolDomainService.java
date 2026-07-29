package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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

  public void updateBuncheolContent(
      final Buncheol buncheol, final String title, final String description) {
    buncheol.updateContent(title, description);
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
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.CANCELLED, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.CANCELLED;
    }
    throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
  }

  /** 호스트에게 아직 끝나지 않은 분철이 있는지 (회원탈퇴 가드용). 판정 기준은 포트 javadoc 참고. */
  public boolean hasUnfinishedBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsUnfinishedByHostId(hostId);
  }
}
