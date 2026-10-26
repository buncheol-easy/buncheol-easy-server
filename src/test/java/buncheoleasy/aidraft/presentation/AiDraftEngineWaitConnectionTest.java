package buncheoleasy.aidraft.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.aidraft.domain.AiDraftCost;
import buncheoleasy.aidraft.domain.AiDraftUsage;
import buncheoleasy.aidraft.infrastructure.AiWorkerClient;
import buncheoleasy.aidraft.infrastructure.AiWorkerDraft;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 엔진을 기다리는 동안 이 요청이 DB 커넥션을 하나도 쥐지 않음을 커넥션 풀 수치로 확인한다. 엔진 앞에서 자격 조회·기록 INSERT 가 트랜잭션을 한 번씩 연다 —
 * OSIV 가 걸려 있으면 그 커넥션이 요청 끝까지 남아 엔진 대기 중 사용 중 1 이 된다.
 *
 * <p>테스트 트랜잭션을 걸지 않는다(그것이 커넥션을 쥔다). 만든 행은 끝나고 지운다. 엔진과 Redis 만 대역이고 자격·기록·그룹 찾기는 실제 DB 를 쓴다.
 */
@SpringBootTest(
    properties = {
      "ai.draft.enabled=true",
      "ai.draft.worker-base-url=http://ai-worker.test:8000",
      "ai.draft.worker-internal-token=test-internal-token"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AI 개최글 엔진 대기 중 DB 커넥션 테스트")
class AiDraftEngineWaitConnectionTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSource dataSource;

  @MockitoBean private AiWorkerClient workerClient;
  @MockitoBean private AiDraftUsage usage;

  private Long hostId;

  @BeforeEach
  void setUp() {
    // 운영진(can_host)은 자격 판정이 정산 계좌만 본다 — 자격 조회가 실제로 DB 를 읽게 한다.
    jdbcTemplate.update(
        "INSERT INTO users (provider, provider_id, email, nickname, profile_completed, can_host,"
            + " settlement_bank, settlement_account, settlement_holder)"
            + " VALUES ('KAKAO', 'ai_osiv_host', 'ai_osiv_host@example.com', 'AiOsivHost', TRUE, TRUE,"
            + " '국민은행', '12345678901234', '김개최')");
    hostId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE provider_id = 'ai_osiv_host'", Long.class);
    given(usage.lock(any(), any(Duration.class))).willReturn(Optional.of("lock"));
    given(usage.addAttempt(anyLong(), any())).willReturn(1L);
    given(usage.addGeneration(anyLong(), any())).willReturn(1L);
    given(usage.addDailyTotal(any())).willReturn(OptionalLong.of(1L));
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM ai_draft_requests WHERE user_id = ?", hostId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", hostId);
  }

  @Test
  void 엔진을_기다리는_동안_커넥션을_쥐지_않는다() throws Exception {
    HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
    int idleBaseline = pool.getHikariPoolMXBean().getActiveConnections();
    AtomicInteger activeDuringEngine = new AtomicInteger(-1);
    willAnswer(
            invocation -> {
              activeDuringEngine.set(pool.getHikariPoolMXBean().getActiveConnections());
              return new AiWorkerDraft(
                  "제목",
                  "본문",
                  "케이타운포유",
                  "https://www.ktown4u.com/iteminfo?goods_no=1",
                  null,
                  List.of(),
                  null,
                  null,
                  null,
                  List.of(),
                  0,
                  new AiDraftCost(null, null, null, 1));
            })
        .given(workerClient)
        .createDraft(any());

    mockMvc
        .perform(
            post("/v1/ai/drafts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(hostId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.ktown4u.com/iteminfo?goods_no=1\"}"))
        .andExpect(status().isOk());

    assertThat(idleBaseline).isZero();
    // 자격 조회·기록 INSERT 를 이미 지난 시점이다. OSIV 면 1 이 된다.
    assertThat(activeDuringEngine.get()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM ai_draft_requests WHERE user_id = ?", String.class, hostId))
        .isEqualTo("SUCCEEDED");
  }
}
