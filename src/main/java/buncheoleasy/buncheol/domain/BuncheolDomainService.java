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

  public void cancelBuncheol(final Buncheol buncheol, final BuncheolStatus expectedStatus) {
    // 상태 위반(이미 취소됨 등)은 도메인에서 BUNCHEOL_CANCEL_NOT_ALLOWED 로 던지고,
    // CAS 실패(동시 요청으로 상태 변동)는 BUNCHEOL_CANCEL_CONFLICT 로 구분한다.
    buncheol.cancel();
    boolean updated = buncheolRepository.updateStatus(buncheol, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_CONFLICT);
    }
  }

  public boolean hasActiveBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsActiveByHostId(hostId);
  }
}
