package buncheoleasy.aidraft.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain(JWT 필터 포함)으로 두 경로의 접근 규칙을 고정한다. 경로를 {@code /v1/buncheols/*} 아래로 옮기면 GET 이
 * 비로그인 공개로 새는데, 그 회귀를 여기서 잡는다.
 *
 * <p>서비스를 대역으로 바꾸지 않는다 — 테스트 설정은 AI 를 꺼 두므로 회원 요청은 실제 서비스의 "꺼짐" 응답까지 간다. 대역 빈을 두면 이 클래스만의 Spring
 * 컨텍스트가 하나 더 생겨, 이미지 빌드(DocsTest 를 384m 힙으로 돈다)와 로컬 기본 힙의 여유를 깎는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AiDraftController 접근 제어 테스트")
class AiDraftControllerTest {

  private static final String DRAFT_BODY =
      "{\"url\":\"https://www.ktown4u.com/iteminfo?goods_no=1\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  private String userBearer() {
    return "Bearer " + jwtTokenProvider.createAccessToken(7L);
  }

  private String adminBearer() {
    jdbcTemplate.update(
        "INSERT INTO admins (login_id, password) VALUES (?, ?)",
        "ai-draft-admin",
        passwordEncoder.encode("admin-password-1234"));
    Long adminId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM admins WHERE login_id = ?", Long.class, "ai-draft-admin");
    return "Bearer " + jwtTokenProvider.createAdminAccessToken(adminId);
  }

  @Nested
  @DisplayName("비로그인")
  class AnonymousTest {

    @Test
    void 초안_요청은_401() throws Exception {
      mockMvc
          .perform(post("/v1/ai/drafts").contentType(MediaType.APPLICATION_JSON).content(DRAFT_BODY))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void 남은_횟수_조회도_401() throws Exception {
      mockMvc.perform(get("/v1/ai/drafts/quota")).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void 관리자_토큰은_두_경로_모두_403() throws Exception {
    // 관리자와 회원은 id 공간이 겹칠 수 있다 — 관리자 토큰이 같은 id 회원의 하루 횟수를 쓰면 안 된다.
    String admin = adminBearer();

    mockMvc
        .perform(
            post("/v1/ai/drafts")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DRAFT_BODY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/v1/ai/drafts/quota").header(HttpHeaders.AUTHORIZATION, admin))
        .andExpect(status().isForbidden());
  }

  @Nested
  @DisplayName("회원 — 기능이 꺼진 테스트 설정")
  class UserTest {

    @Test
    void 남은_횟수_조회는_200_으로_꺼짐을_알린다() throws Exception {
      mockMvc
          .perform(get("/v1/ai/drafts/quota").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.enabled").value(false))
          .andExpect(jsonPath("$.dailyLimit").value(10))
          .andExpect(jsonPath("$.remainingToday").value(0));
    }

    @Test
    void 초안_요청은_409_AI_007_코드로_나간다() throws Exception {
      mockMvc
          .perform(
              post("/v1/ai/drafts")
                  .header(HttpHeaders.AUTHORIZATION, userBearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(DRAFT_BODY))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("AI-007"));
    }
  }
}
