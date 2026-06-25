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
   * 분철을 RECRUITING 일 때만 {@code newStatus}(CONFIRMED 또는 CANCELLED) 로 전이하는 CAS. 마감 스케줄러가 다중 인스턴스 환경에서
   * 중복 판정하지 않도록, 선점에 성공한 한쪽만 true 를 받는다.
   *
   * @return 전이에 성공하면 true, 이미 마감 판정됐거나 RECRUITING 이 아니면 false
   */
  public boolean finalizeBuncheol(
      final Long buncheolId, final BuncheolStatus newStatus, final Instant now) {
    return buncheolRepository.finalizeIfRecruiting(buncheolId, newStatus, now) > 0;
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
   * 호스트의 분철 취소 (RECRUITING → HOST_CANCELLED CAS). 모집 중이 아니면 상태 위반으로 막는다. 인원 미달 자동취소(CANCELLED)와 달리 목록·상세에서
   * 숨겨지며(하드 삭제 대신 소프트 숨김), 활성 참여 cascade 취소·알림은 호출 측에서 동일하게 처리한다.
   */
  public void cancelBuncheol(final Long buncheolId, final Instant now) {
    if (!finalizeBuncheol(buncheolId, BuncheolStatus.HOST_CANCELLED, now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }

  public boolean hasActiveBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsActiveByHostId(hostId);
  }
}
