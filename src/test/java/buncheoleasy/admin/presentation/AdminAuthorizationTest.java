package buncheoleasy.admin.presentation;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 관리자 인가 규칙 통합 테스트. 실제 SecurityFilterChain(JWT 필터 포함)을 켠 채로 {@code /v1/admin/**}·공지 관리 API 가
 * ROLE_ADMIN 으로 잠겨 있는지, 관리자 토큰이 유저 API 로 새지 않는지(id 공간 겹침 위장 차단) 검증한다 — 매처 순서/패턴 변경으로 권한 경계가 조용히
 * 뚫리는 회귀를 막는 방어막.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 인가 규칙 통합 테스트")
class AdminAuthorizationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  private String userBearer() {
    return "Bearer " + jwtTokenProvider.createAccessToken(1L);
  }

  private String adminBearer() {
    return "Bearer " + jwtTokenProvider.createAdminAccessToken(insertAdmin());
  }

  private Long insertAdmin() {
    jdbcTemplate.update(
        "INSERT INTO admins (login_id, password) VALUES (?, ?)",
        "buncheol-admin",
        passwordEncoder.encode("admin-password-1234"));
    return jdbcTemplate.queryForObject(
        "SELECT id FROM admins WHERE login_id = ?", Long.class, "buncheol-admin");
  }

  @Nested
  @DisplayName("admin API 접근 테스트")
  class AdminApiTest {

    @Test
    void 비로그인이면_401() throws Exception {
      mockMvc.perform(get("/v1/admin/payments/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void 유저_토큰이면_403() throws Exception {
      mockMvc
          .perform(
              get("/v1/admin/payments/summary").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isForbidden());

      mockMvc
          .perform(get("/v1/admin/payments").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isForbidden());

      mockMvc
          .perform(
              get("/v1/admin/shipping-fee-paybacks").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isForbidden());
    }

    @Test
    void 배송비_환급_검수_API_도_관리자_토큰으로_접근할_수_있다() throws Exception {
      mockMvc
          .perform(
              get("/v1/admin/shipping-fee-paybacks")
                  .header(HttpHeaders.AUTHORIZATION, adminBearer()))
          .andExpect(status().isOk());
    }

    @Test
    void 관리자_토큰이면_접근할_수_있다() throws Exception {
      String bearer = adminBearer();

      mockMvc
          .perform(get("/v1/admin/payments/summary").header(HttpHeaders.AUTHORIZATION, bearer))
          .andExpect(status().isOk());

      mockMvc
          .perform(get("/v1/admin/me").header(HttpHeaders.AUTHORIZATION, bearer))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.loginId").value("buncheol-admin"));
    }

    @Test
    void 관리자_토큰으로는_유저_API_에_접근할_수_없다() throws Exception {
      // admins.id 와 users.id 가 겹쳐도 관리자 토큰이 유저로 위장할 수 없어야 한다.
      mockMvc
          .perform(get("/v1/users/me").header(HttpHeaders.AUTHORIZATION, adminBearer()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("ID/PW 로그인 테스트")
  class LoginTest {

    @Test
    void 올바른_자격증명이면_비로그인_상태에서_토큰을_발급받는다() throws Exception {
      insertAdmin();

      mockMvc
          .perform(
              post("/v1/admin/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"loginId\": \"buncheol-admin\", \"password\": \"admin-password-1234\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void 로그인_permitAll_은_POST_에만_적용된다() throws Exception {
      // GET /v1/admin/auth/login 은 ADMIN_PATHS(/v1/admin/**) 매처로 떨어져 잠긴다 — 오버매칭 회귀 방지.
      mockMvc.perform(get("/v1/admin/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_자격증명이면_401() throws Exception {
      insertAdmin();

      mockMvc
          .perform(
              post("/v1/admin/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"loginId\": \"buncheol-admin\", \"password\": \"wrong\"}"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("공지 관리 API 테스트")
  class NoticeAdminApiTest {

    @Test
    void 유저는_공지를_작성할_수_없다() throws Exception {
      mockMvc
          .perform(post("/v1/notices").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isForbidden());
    }

    @Test
    void 유저는_공지를_고정할_수_없다() throws Exception {
      mockMvc
          .perform(put("/v1/notices/1/pin").header(HttpHeaders.AUTHORIZATION, userBearer()))
          .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_공지_고정_인가를_통과한다() throws Exception {
      // 존재하지 않는 공지라 404 — 403(인가 거부)이 아니라는 것이 검증 포인트.
      mockMvc
          .perform(
              put("/v1/notices/999999/pin").header(HttpHeaders.AUTHORIZATION, adminBearer()))
          .andExpect(status().isNotFound());
    }
  }
}
