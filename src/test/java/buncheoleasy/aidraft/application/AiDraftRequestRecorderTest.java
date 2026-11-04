package buncheoleasy.aidraft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.aidraft.domain.AiDraftCost;
import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 기록 표(ai_draft_requests)의 열 매핑과 RUNNING → 마감 흐름을 실제 DB 로 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AiDraftRequestRecorder 통합 테스트")
class AiDraftRequestRecorderTest {

  @Autowired private AiDraftRequestRecorder recorder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager em;

  private Long userId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "ai_draft_user");
  }

  private Map<String, Object> row(final Long id) {
    em.flush();
    em.clear();
    return jdbcTemplate.queryForMap("SELECT * FROM ai_draft_requests WHERE id = ?", id);
  }

  @Test
  void 주소_요청은_호스트와_주소를_남기고_RUNNING_으로_시작한다() {
    Long id =
        recorder.start(
            userId, AiDraftSource.of("https://Shop.Weverse.io/ko/shop/GL_KRW/artists/1", null));

    Map<String, Object> row = row(id);

    assertThat(row.get("user_id")).isEqualTo(userId);
    assertThat(row.get("source_type")).isEqualTo("URL");
    assertThat(row.get("source_host")).isEqualTo("shop.weverse.io");
    assertThat(row.get("source_url")).isEqualTo("https://Shop.Weverse.io/ko/shop/GL_KRW/artists/1");
    assertThat(row.get("raw_text_length")).isNull();
    assertThat(row.get("status")).isEqualTo("RUNNING");
    assertThat(row.get("finished_at")).isNull();
    assertThat(row.get("created_at")).isNotNull();
  }

  @Test
  void 붙여_넣은_글은_길이만_남긴다() {
    String pasted = "앨범 14,100원 포토북";
    Long id = recorder.start(userId, AiDraftSource.of(null, pasted));

    Map<String, Object> row = row(id);

    assertThat(row.get("source_type")).isEqualTo("TEXT");
    assertThat(row.get("raw_text_length")).isEqualTo(pasted.length());
    assertThat(row.get("source_url")).isNull();
    assertThat(row.get("source_host")).isNull();
    // 글 자체를 담을 열이 표에 없다.
    assertThat(row).doesNotContainValue(pasted);
  }

  @Test
  void 성공하면_비용_토큰_호출수_그룹_시간을_남기고_마감한다() {
    Long id = recorder.start(userId, AiDraftSource.of("https://a.com/x", null));

    recorder.succeed(
        id, new AiDraftCost(new BigDecimal("0.0017"), 16200, 980, 2), 12L, Duration.ofMillis(7300));
    Map<String, Object> row = row(id);

    assertThat(row.get("status")).isEqualTo("SUCCEEDED");
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.0017");
    assertThat(row.get("input_tokens")).isEqualTo(16200);
    assertThat(row.get("output_tokens")).isEqualTo(980);
    assertThat(row.get("ai_call_count")).isEqualTo(2);
    assertThat(row.get("matched_group_id")).isEqualTo(12L);
    assertThat(row.get("duration_ms")).isEqualTo(7300L);
    assertThat(row.get("error_code")).isNull();
    assertThat(row.get("finished_at")).isNotNull();
  }

  @Test
  void 실패하면_오류_코드를_남기고_비용은_비운다() {
    Long id = recorder.start(userId, AiDraftSource.of("https://smartstore.naver.com/x", null));

    recorder.fail(id, ErrorCode.AI_DRAFT_FETCH_FAILED, Duration.ofMillis(1200));
    Map<String, Object> row = row(id);

    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("error_code")).isEqualTo("AI-002");
    assertThat(row.get("cost_usd")).isNull();
    assertThat(row.get("duration_ms")).isEqualTo(1200L);
    assertThat(row.get("finished_at")).isNotNull();
  }

  @Test
  void 기록이_없으면_마감을_건너뛴다() {
    assertThatCode(
            () -> recorder.fail(-1L, ErrorCode.AI_DRAFT_FETCH_FAILED, Duration.ZERO))
        .doesNotThrowAnyException();
  }
}
