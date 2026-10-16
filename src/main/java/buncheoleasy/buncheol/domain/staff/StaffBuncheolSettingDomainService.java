package buncheoleasy.buncheol.domain.staff;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffBuncheolSettingDomainService {

  private final StaffBuncheolSettingRepository staffBuncheolSettingRepository;

  /** 분철을 만들기 전에 부른다. 쓰기 전에 실패해야 잘못된 오픈 시각으로 분철이 반쯤 만들어지지 않는다. */
  public void validateOpensAt(final Instant opensAt, final Instant deadline, final Instant now) {
    StaffBuncheolSetting.validateOpensAt(opensAt, deadline, now);
  }

  public StaffBuncheolSetting create(
      final Long buncheolId, final Instant opensAt, final Instant deadline, final Instant now) {
    return staffBuncheolSettingRepository.save(
        StaffBuncheolSetting.create(buncheolId, opensAt, deadline, now));
  }

  public Optional<StaffBuncheolSetting> findSetting(final Long buncheolId) {
    return staffBuncheolSettingRepository.findByBuncheolId(buncheolId);
  }

  public boolean isBeforeOpenByDbClock(final Long buncheolId) {
    return staffBuncheolSettingRepository.isBeforeOpenByDbClock(buncheolId);
  }
}
