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
    // TODO: 유효성 검사 필요 (예: 상태가 XX일 떄는 OO값 수정 불가 등) 추후 하겠음.
    buncheolRepository.update(buncheol.getId(), params);
  }

  public void cancelBuncheol(final Buncheol buncheol) {
    buncheol.cancel();
    buncheolRepository.updateStatus(buncheol.getId(), buncheol.getStatus());
  }
}
