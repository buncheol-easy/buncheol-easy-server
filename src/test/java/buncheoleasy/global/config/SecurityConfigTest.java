package buncheoleasy.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
                  null, new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 20))
          .willReturn(new CursorResponse<BuncheolSummaryResponse>(List.of(), null, false));

      mockMvc.perform(get("/v1/buncheols")).andExpect(status().isOk());
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
}
