package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.HoldStaffBuncheolRequest;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영진 개최는 분철·자리·오픈 시각을 <b>한 트랜잭션</b>으로 저장한다. 설정 저장을 커밋 뒤 이벤트로 빼면 커밋 전에는 설정 행이 안 보이고,
 * 별도 트랜잭션으로 빼면 바깥을 롤백해도 설정 행이 남고, 서비스의 트랜잭션이 빠지면 중간 실패 때 분철이 남는다 — 셋 다 아래 테스트가
 * 잡는다. 단위 테스트(mock)로는 커밋 경계가 보이지 않아 실제 H2 로 확인한다.
 *
 * <p>빈 교체 없이 다른 어댑터 테스트와 같은 컨텍스트를 쓴다(테스트 JVM 힙 상한 512m 에서 컨텍스트를 더 늘리지 않는다). 그래서 실패는
 * 설정 저장소를 바꿔 끼우는 대신 바깥 트랜잭션 롤백과 실제 검증 오류로 만든다. 커밋하는 경로는 이미지 업로드가 커밋 뒤 비동기로 돌아
 * 정리와 겹치므로 여기서 다루지 않는다. 픽스처는 커밋되므로 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("운영진 분철 개최 통합 테스트")
class StaffBuncheolHoldIntegrationTest {

  private static final String STAFF_PROVIDER_ID = "staff-hold";

  @Autowired private BuncheolService buncheolService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  private Long staffId;
  private Long groupId;
  private Long memberId;
  private Long codeMemberId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update(
        "INSERT INTO users (provider, provider_id, email, nickname, profile_completed, can_host,"
            + " settlement_bank, settlement_account, settlement_holder)"
            + " VALUES ('KAKAO', ?, ?, 'staffhold', true, true, '국민', '123-456', '운영진')",
        STAFF_PROVIDER_ID,
        STAFF_PROVIDER_ID + "@example.com");
    staffId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE provider_id = ?", Long.class, STAFF_PROVIDER_ID);
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "운영진 개최 그룹");
    memberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "주헌");
    codeMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "형원");
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM staff_buncheol_settings WHERE buncheol_id IN"
            + " (SELECT id FROM buncheols WHERE host_id = ?)",
        staffId);
    jdbcTemplate.update(
        "DELETE FROM buncheol_members WHERE buncheol_id IN"
            + " (SELECT id FROM buncheols WHERE host_id = ?)",
        staffId);
    jdbcTemplate.update("DELETE FROM buncheols WHERE host_id = ?", staffId);
    jdbcTemplate.update("DELETE FROM group_members WHERE group_id = ?", groupId);
    jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", staffId);
  }

  private HoldStaffBuncheolRequest request(final Instant deadline, final Instant opensAt) {
    return new HoldStaffBuncheolRequest(
        groupId,
        "몬엑 분철",
        null,
        "공식 스토어",
        deadline,
        opensAt,
        1,
        3000,
        null,
        null,
        0,
        List.of(
            new BuncheolMemberRequest(memberId, 15_000L),
            new BuncheolMemberRequest(
                codeMemberId, 15_000L, BuncheolMemberAccessType.CODE_ONLY)));
  }

  private static List<ImageFile> images() {
    return List.of(new ImageFile("cover.jpg", "image/jpeg", new byte[] {1, 2, 3}));
  }

  @Test
  void 분철_자리_오픈_시각이_한_트랜잭션에_저장되고_함께_롤백된다() {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant opensAt = deadline.minus(1, ChronoUnit.DAYS);

    Long buncheolId =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  Long created =
                      buncheolService.holdStaffBuncheol(
                          staffId, request(deadline, opensAt), images());
                  // 커밋 전, 같은 트랜잭션 안에서 세 표가 모두 보인다.
                  assertThat(flowTypeOf(created)).isEqualTo("LEGACY");
                  assertThat(countMembers(created)).isEqualTo(2);
                  assertThat(opensAtOf(created)).isEqualTo(Timestamp.from(opensAt));
                  status.setRollbackOnly();
                  return created;
                });

    assertThat(countBuncheols()).isZero();
    assertThat(countMembers(buncheolId)).isZero();
    assertThat(countSettings(buncheolId)).isZero();
  }

  // 닫힌 자리는 분철 INSERT 뒤에 거절된다(C-001). holdStaffBuncheol 자신의 트랜잭션이 없으면 그 INSERT 가
  // 따로 커밋돼 분철이 남는다. 이미지 이벤트 발행 전에 실패하므로 비동기 업로드와 겹치지 않는다.
  @Test
  void 바깥_트랜잭션_없이도_분철_INSERT_뒤에_실패하면_분철이_남지_않는다() {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    HoldStaffBuncheolRequest closedSlot =
        new HoldStaffBuncheolRequest(
            groupId,
            "몬엑 분철",
            null,
            "공식 스토어",
            deadline,
            deadline.minus(1, ChronoUnit.DAYS),
            1,
            3000,
            null,
            null,
            0,
            List.of(
                new BuncheolMemberRequest(memberId, 15_000L, BuncheolMemberAccessType.CLOSED)));

    assertThatThrownBy(() -> buncheolService.holdStaffBuncheol(staffId, closedSlot, images()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

    assertThat(countBuncheols()).isZero();
  }

  private String flowTypeOf(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT flow_type FROM buncheols WHERE id = ?", String.class, buncheolId);
  }

  private Timestamp opensAtOf(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT opens_at FROM staff_buncheol_settings WHERE buncheol_id = ?",
        Timestamp.class,
        buncheolId);
  }

  private int countBuncheols() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheols WHERE host_id = ?", Integer.class, staffId);
  }

  private int countMembers(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM buncheol_members WHERE buncheol_id = ?", Integer.class, buncheolId);
  }

  private int countSettings(final Long buncheolId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM staff_buncheol_settings WHERE buncheol_id = ?",
        Integer.class,
        buncheolId);
  }
}
