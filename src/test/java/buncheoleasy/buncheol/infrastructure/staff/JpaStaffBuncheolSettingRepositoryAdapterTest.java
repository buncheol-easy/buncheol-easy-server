package buncheoleasy.buncheol.infrastructure.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSetting;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingRepository;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** {@code isBeforeOpenByDbClock} 은 MySQL 함수({@code UTC_TIMESTAMP()})를 써서 여기서 돌리지 않고 로컬 MySQL 로 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaStaffBuncheolSettingRepositoryAdapter 테스트")
class JpaStaffBuncheolSettingRepositoryAdapterTest {

  @Autowired private StaffBuncheolSettingRepository staffBuncheolSettingRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Instant now;
  private Instant deadline;
  private Long hostId;
  private Long groupId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host-staff");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "운영진 설정 그룹");
    now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    deadline = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }

  private Long saveBuncheol() {
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(
                groupId, "운영진 분철", null, "스토어", deadline, 1, 2300, 2300, FlowType.LEGACY, null),
            now);
    buncheolRepository.save(buncheol);
    return buncheol.getId();
  }

  @Test
  void 저장하면_분철_id_로_다시_읽힌다() {
    Long buncheolId = saveBuncheol();
    Instant opensAt = now.plus(1, ChronoUnit.DAYS);

    staffBuncheolSettingRepository.save(
        StaffBuncheolSetting.create(buncheolId, opensAt, deadline, now));
    em.flush();
    em.clear();

    StaffBuncheolSetting found = staffBuncheolSettingRepository.findByBuncheolId(buncheolId).orElseThrow();
    assertThat(found.getOpensAt()).isEqualTo(opensAt);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(found.isNew()).isFalse();
  }

  @Test
  void 행이_없으면_빈_값이다() {
    Long buncheolId = saveBuncheol();

    assertThat(staffBuncheolSettingRepository.findByBuncheolId(buncheolId)).isEmpty();
  }

  @Test
  void 같은_분철에_두_번_저장하면_덮어쓰지_않고_실패한다() {
    Long buncheolId = saveBuncheol();
    staffBuncheolSettingRepository.save(
        StaffBuncheolSetting.create(buncheolId, now.plus(1, ChronoUnit.DAYS), deadline, now));
    em.flush();
    em.clear();

    assertThatThrownBy(
            () ->
                staffBuncheolSettingRepository.save(
                    StaffBuncheolSetting.create(
                        buncheolId, now.plus(2, ChronoUnit.DAYS), deadline, now)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 여러_분철의_오픈_시각을_한_번에_읽고_행이_있는_분철만_담는다() {
    Long first = saveBuncheol();
    Long second = saveBuncheol();
    Long withoutSetting = saveBuncheol();
    Instant firstOpensAt = now.plus(1, ChronoUnit.DAYS);
    Instant secondOpensAt = now.plus(2, ChronoUnit.DAYS);
    staffBuncheolSettingRepository.save(
        StaffBuncheolSetting.create(first, firstOpensAt, deadline, now));
    staffBuncheolSettingRepository.save(
        StaffBuncheolSetting.create(second, secondOpensAt, deadline, now));
    em.flush();
    em.clear();

    Map<Long, Instant> opensAtById =
        staffBuncheolSettingRepository.findOpensAtByBuncheolIds(
            List.of(first, second, withoutSetting));

    assertThat(opensAtById).containsOnly(
        Map.entry(first, firstOpensAt), Map.entry(second, secondOpensAt));
  }

  @Test
  void 빈_id_목록이면_빈_맵이다() {
    assertThat(staffBuncheolSettingRepository.findOpensAtByBuncheolIds(List.of())).isEmpty();
  }

  @Test
  void 분철이_없으면_FK_로_저장되지_않는다() {
    assertThatThrownBy(
            () ->
                staffBuncheolSettingRepository.save(
                    StaffBuncheolSetting.create(
                        999_999L, now.plus(1, ChronoUnit.DAYS), deadline, now)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
