package buncheoleasy.buncheol.infrastructure.staff;

import buncheoleasy.buncheol.domain.staff.StaffBuncheolSetting;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaStaffBuncheolSettingRepositoryAdapter implements StaffBuncheolSettingRepository {

  private final JpaStaffBuncheolSettingRepository jpaStaffBuncheolSettingRepository;

  /** 직접 넣는 id 라 INSERT 가 커밋 때로 밀린다. 바로 내보내 PK·FK 위반이 호출한 자리에서 드러나게 한다. */
  @Override
  public StaffBuncheolSetting save(final StaffBuncheolSetting setting) {
    return jpaStaffBuncheolSettingRepository.saveAndFlush(setting);
  }

  @Override
  public Optional<StaffBuncheolSetting> findByBuncheolId(final Long buncheolId) {
    return jpaStaffBuncheolSettingRepository.findById(buncheolId);
  }

  @Override
  public Map<Long, Instant> findOpensAtByBuncheolIds(final Collection<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return Map.of();
    }
    return jpaStaffBuncheolSettingRepository.findAllByBuncheolIdIn(buncheolIds).stream()
        .collect(
            Collectors.toMap(StaffBuncheolSetting::getBuncheolId, StaffBuncheolSetting::getOpensAt));
  }

  @Override
  public boolean isBeforeOpenByDbClock(final Long buncheolId) {
    return jpaStaffBuncheolSettingRepository.countBeforeOpenByDbClock(buncheolId) > 0;
  }
}
