package buncheoleasy.buncheol.domain.staff;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface StaffBuncheolSettingRepository {

  StaffBuncheolSetting save(StaffBuncheolSetting setting);

  Optional<StaffBuncheolSetting> findByBuncheolId(Long buncheolId);

  /** 설정 행이 있는 분철만 키로 담는다. 목록 카드가 페이지당 한 번 부른다. */
  Map<Long, Instant> findOpensAtByBuncheolIds(Collection<Long> buncheolIds);

  /**
   * DB 시각({@code UTC_TIMESTAMP()})으로 아직 열리지 않았는가. 오픈 조건 참여 INSERT 가 0행일 때 원인(오픈 전인지, 모집이 끝났는지)을
   * 같은 시계로 가르는 데 쓴다. 최신 커밋을 읽는 잠금 읽기라 그사이 운영 SQL 로 바뀐 오픈 시각도 본다.
   */
  boolean isBeforeOpenByDbClock(Long buncheolId);
}
