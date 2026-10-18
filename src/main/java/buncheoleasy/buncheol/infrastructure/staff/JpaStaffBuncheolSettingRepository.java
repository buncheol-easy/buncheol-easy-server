package buncheoleasy.buncheol.infrastructure.staff;

import buncheoleasy.buncheol.domain.staff.StaffBuncheolSetting;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaStaffBuncheolSettingRepository extends JpaRepository<StaffBuncheolSetting, Long> {

  List<StaffBuncheolSetting> findAllByBuncheolIdIn(Collection<Long> buncheolIds);

  // UTC_TIMESTAMP() 는 MySQL 함수라 H2 테스트로는 돌지 않는다 (로컬 MySQL 로 확인).
  @Query(
      value =
          "SELECT COUNT(*) FROM staff_buncheol_settings "
              + "WHERE buncheol_id = :buncheolId AND opens_at > UTC_TIMESTAMP() FOR SHARE",
      nativeQuery = true)
  long countBeforeOpenByDbClock(@Param("buncheolId") Long buncheolId);
}
