package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolDomainService {

  private final BuncheolRepository buncheolRepository;

  public Buncheol createBuncheol(final Long hostId, final BuncheolParams params) {
    return buncheolRepository.save(Buncheol.create(hostId, params));
  }

  public Buncheol getBuncheol(final Long id) {
    return buncheolRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }

  public void updateBuncheol(final Buncheol buncheol, final BuncheolParams params) {
    buncheolRepository.update(buncheol.getId(), params);
  }

  public void cancelBuncheol(final Buncheol buncheol, final BuncheolStatus expectedStatus) {
    buncheol.cancel();
    updateBuncheolStatus(buncheol, expectedStatus);
  }

  public void advanceBuncheolStatus(
      final Buncheol buncheol,
      final BuncheolStatus nextStatus,
      final BuncheolStatus expectedStatus) {
    buncheol.advanceStatus(nextStatus);
    updateBuncheolStatus(buncheol, expectedStatus);
  }

  private void updateBuncheolStatus(final Buncheol buncheol, final BuncheolStatus expectedStatus) {
    boolean updated = buncheolRepository.updateStatus(buncheol, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED);
    }
  }
}
