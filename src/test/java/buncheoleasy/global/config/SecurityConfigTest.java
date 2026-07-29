package buncheoleasy.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.global.page.CursorResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /v1/buncheols/{id}} 가 비로그인 허용으로 열리면서 동일 단일 세그먼트 패턴이 {@code /v1/buncheols/me} 까지 함께 매칭한다. 본
 * 테스트는 SecurityConfig 의 매칭 우선순위가 {@code /me} 를 인증 필수로, 그 외 path 는 공개로 분기하는지 보장한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig 접근 제어 테스트")
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;
  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;
  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  // 실제 구현은 Redis 를 쓰므로 접근 제어만 보는 이 테스트에서는 대역으로 둔다.
  @MockitoBean private FeedbackService feedbackService;

  @Nested
  @DisplayName("/v1/buncheols/* 비로그인 허용 경로")
  class PublicGetPaths {

    @Test
    void 분철_단건_상세_조회는_비로그인이어도_401이_아니다() throws Exception {
      given(buncheolDetailQueryService.getDetail(10L, null))
          .willReturn(
              new BuncheolDetailResponse(
                  10L,
                  "분철",
                  "그룹",
                  "스토어",
                  Instant.parse("2026-06-01T12:00:00Z"),
                  null,
                  null,
                  0,
                  0,
                  List.of(),
                  List.of(),
                  List.of(),
                  false,
                  null));

      mockMvc.perform(get("/v1/buncheols/{id}", 10L)).andExpect(status().isOk());
    }

    @Test
    void 분철_목록_조회는_비로그인이어도_401이_아니다() throws Exception {
      given(
              buncheolListQueryService.search(
                  null,
                  new BuncheolSearchCondition(null, null, null),
                  BuncheolListCursor.firstPage(),
                  20))
          .willReturn(new CursorResponse<BuncheolSummaryResponse>(List.of(), null, false));

      mockMvc.perform(get("/v1/buncheols")).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("POST /v1/feedbacks — POST 한정 비로그인 허용")
  class FeedbackPath {

    /**
     * 의견 보내기는 <b>로그인이 안 된 상태에서 남기는 의견</b>을 받는 것이 목적이라 permitAll 이다. 이 매처가 지워지면 {@code
     * anyRequest().hasRole("USER")} 로 떨어져 401 이 되며 이 테스트가 깨진다.
     */
    @Test
    void 의견_보내기는_비로그인이어도_401이_아니다() throws Exception {
      mockMvc
          .perform(
              post("/v1/feedbacks")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"의견\"}"))
          .andExpect(status().isNoContent());
    }

    /** permitAll 이 POST 한정임을 잠근다 — 메서드 무관으로 넓어지면 이 테스트가 깨진다. */
    @Test
    void 의견_조회는_비로그인이면_401() throws Exception {
      mockMvc.perform(get("/v1/feedbacks")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("AUTH_REQUIRED_GET_PATHS — 단일 세그먼트 매처보다 우선 적용")
  class AuthRequiredPaths {

    @Test
    void 내_개최_분철_조회는_비로그인이면_401() throws Exception {
      mockMvc.perform(get("/v1/buncheols/me")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("호스트 전용 GET — 단일 세그먼트 공개 매처에 걸리지 않음")
  class HostOnlyGetPaths {

    /**
     * {@code /v1/buncheols/{id}/management} 는 두 세그먼트라 공개 매처({@code /v1/buncheols/*}) 에 걸리지 않고
     * {@code anyRequest().authenticated()} 로 보호된다. 공개 매처가 {@code /v1/buncheols/**} 로 넓어지면 이 테스트가
     * 깨지며 노출 회귀를 잡아낸다.
     */
    @Test
    void 개최자_분철_관리_조회는_비로그인이면_401() throws Exception {
      mockMvc
          .perform(get("/v1/buncheols/{id}/management", 10L))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET 한정 — 다른 메서드는 공개 매처에 걸리지 않음")
  class NonGetMethods {

    @Test
    void 분철_삭제는_비로그인이면_401() throws Exception {
      mockMvc.perform(delete("/v1/buncheols/{id}", 10L)).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("입금 웹훅 — 인증 없이 도달하되 컨트롤러가 키를 검증")
  class DepositWebhookPath {

    private static final String BODY =
        """
        {"order_number":"999999","order_status":"매칭완료"}
        """;

    /**
     * 페이액션은 JWT 를 보내지 않으므로 Security 단계에서 막히면 안 된다. 올바른 키로 200 이 나온다는 것이 곧 Security 를 통과했다는 증거다
     * (존재하지 않는 참여라 서비스는 알림만 남기고 정상 응답한다).
     */
    @Test
    void 올바른_키면_비로그인이어도_200_과_success_응답() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .header("x-webhook-key", "test-webhook-key")
                  .header("x-mall-id", "test-mall-id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"));
    }

    /**
     * 상점ID 는 비밀값이 아니라 인증 강도에 기여하지 않으므로 필수로 두지 않는다. 페이액션이 이 헤더를 싣지 않아도 자동확인이 죽지 않아야 한다(문서상 헤더 구성이
     * 엇갈려 실제로 올지 확신할 수 없다).
     */
    @Test
    void 상점ID_헤더가_없어도_비밀키만_맞으면_200() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .header("x-webhook-key", "test-webhook-key")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"));
    }

    /** 문서가 헤더 이름을 x-webhook-key / X-API-Key 로 엇갈리게 안내해 둘 다 수용한다. */
    @Test
    void x_api_key_헤더로_와도_200() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .header("x-api-key", "test-webhook-key")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"));
    }

    /** 상점ID 가 실려 왔는데 우리 설정과 다르면 거른다. */
    @Test
    void 상점ID가_실려왔는데_불일치면_401() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .header("x-webhook-key", "test-webhook-key")
                  .header("x-mall-id", "other-mall")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isUnauthorized());
    }

    /** 공개 경로라도 컨트롤러가 공유 비밀키를 검증한다. */
    @Test
    void 키가_틀리면_401() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .header("x-webhook-key", "wrong-key")
                  .header("x-mall-id", "test-mall-id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void 키가_없으면_401() throws Exception {
      mockMvc
          .perform(
              post("/v1/deposits/payaction/webhook")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isUnauthorized());
    }
  }
}
