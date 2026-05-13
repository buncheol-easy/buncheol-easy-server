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

  public void updateBuncheolContent(
      final Buncheol buncheol, final String title, final String description) {
    buncheol.updateContent(title, description);
  }

  public void cancelBuncheol(final Buncheol buncheol, final BuncheolStatus expectedStatus) {
    buncheol.cancel();
    boolean updated = buncheolRepository.updateStatus(buncheol, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }

  public boolean hasActiveBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsActiveByHostId(hostId);
  }
}
