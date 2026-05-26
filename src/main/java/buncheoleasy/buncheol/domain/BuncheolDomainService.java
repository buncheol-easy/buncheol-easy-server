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

  public void cancelBuncheol(final Buncheol buncheol) {
    // 호스트 본인만 자신의 분철을 취소할 수 있어 다중 동시 요청이 의미 있는 경합을 만들지 않는다.
    // 따라서 CAS 없이 도메인 메서드로 상태 위반(BUNCHEOL_CANCEL_NOT_ALLOWED) 만 검증하고
    // managed 엔티티의 in-memory 전이를 JPA dirty checking 으로 커밋 시점에 반영한다.
    buncheol.cancel();
  }

  public boolean hasActiveBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsActiveByHostId(hostId);
  }
}
