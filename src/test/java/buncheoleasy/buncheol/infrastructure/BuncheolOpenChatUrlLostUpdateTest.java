package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.domain.Buncheol;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 링크 수정 flush 가 <b>어떤 컬럼 집합을 쓰는지</b> 고정하는 회귀 테스트.
 *
 * <p>링크 전용 수정은 모집중이 끝난 뒤에도 열려 있어(취소만 차단) 마감 CAS·성사 확정 CAS 와 같은 행을 두고 겹친다. {@code Buncheol} 에
 * {@code @DynamicUpdate} 가 없으면 flush 가 <b>행 전체</b>를 로드 시점 stale 값으로 다시 써서, 그 사이 기록된
 * {@code status}·{@code finalized_at} 이 되돌아간다(lost update). 애노테이션을 떼면 이 테스트가 깨진다.
 *
 * <p>⚠️ <b>동시성 자체를 재현하지는 않는다.</b> 클래스에 {@code @Transactional} 이 걸려 있어 아래 {@code jdbcTemplate}
 * UPDATE 는 EntityManager 와 <b>같은 트랜잭션·같은 커넥션</b>에서 실행된다 — 별도 트랜잭션 커밋이 아니다. 검증 대상은 "flush 가 쓰는
 * 컬럼 집합" 이며, 실제 경합 안전성은 CAS 쿼리 쪽이 담보한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("오픈채팅 링크 수정 lost update 회귀 테스트")
class BuncheolOpenChatUrlLostUpdateTest {

  @Autowired private BuncheolService buncheolService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long groupId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_openchat");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "링크테스트그룹");
  }

  @Test
  void 링크_flush_가_그_사이_기록된_상태를_되돌리지_않는다() {
    Instant finalizedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Long buncheolId = insertBuncheol();

    // 개최자가 링크 수정을 시작한다 — 이 시점 스냅샷은 RECRUITING / finalized_at NULL.
    Buncheol buncheol = em.find(Buncheol.class, buncheolId);
    assertThat(buncheol.getStatus().name()).isEqualTo("RECRUITING");

    // 마감 CAS 가 같은 행을 전이시킨 상황. 영속성 컨텍스트를 거치지 않아 위 스냅샷은 낡은 채로 남는다.
    jdbcTemplate.update(
        "UPDATE buncheols SET status = 'CONFIRMED', finalized_at = ? WHERE id = ?",
        Timestamp.from(finalizedAt),
        buncheolId);

    // 실제 진입 경로로 링크를 고친다 — 서비스가 다른 필드를 함께 더럽히게 되어도 이 테스트가 잡는다.
    buncheolService.updateOpenChatUrl(hostId, buncheolId, "https://open.kakao.com/o/gAbCdEf");
    em.flush();

    // 링크는 바뀌고, 먼저 기록된 상태는 값까지 그대로 살아 있어야 한다.
    assertThat(openChatUrl(buncheolId)).isEqualTo("https://open.kakao.com/o/gAbCdEf");
    assertThat(status(buncheolId)).isEqualTo("CONFIRMED");
    assertThat(finalizedAtOf(buncheolId)).isEqualTo(Timestamp.from(finalizedAt));
  }

  private Long insertBuncheol() {
    jdbcTemplate.update(
        "INSERT INTO buncheols (host_id, group_id, title, purchase_site, deadline,"
            + " min_headcount, gs25_shipping_fee, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        hostId,
        groupId,
        "링크 수정 테스트 분철",
        "store",
        Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)),
        1,
        3000,
        "RECRUITING");
    return jdbcTemplate.queryForObject(
        "SELECT id FROM buncheols WHERE host_id = ? ORDER BY id DESC LIMIT 1", Long.class, hostId);
  }

  private String status(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private String openChatUrl(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT open_chat_url FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private Timestamp finalizedAtOf(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT finalized_at FROM buncheols WHERE id = ?", Timestamp.class, buncheolId);
  }
}
