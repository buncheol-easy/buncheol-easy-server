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

  /** 호스트의 분철 취소 (RECRUITING → CANCELLED CAS). 모집 중이 아니면 상태 위반으로 막는다. */
  public void cancelBuncheol(final Long buncheolId, final Instant now) {
    if (!finalizeBuncheol(buncheolId, BuncheolStatus.CANCELLED, now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }

  public boolean hasActiveBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsActiveByHostId(hostId);
  }
}
