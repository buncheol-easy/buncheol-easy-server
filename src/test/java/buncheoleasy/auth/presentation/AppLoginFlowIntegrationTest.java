package buncheoleasy.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.domain.AppLoginCodeStore;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * 실제 SecurityFilterChain 을 켠 채로 앱 로그인 입구가 열려 있는지, 카카오 앱 로그인 시작·실패가 앱 주소로 가는지 확인한다. Redis 를 쓰는
 * 저장소만 대역으로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("앱 로그인 입구 통합 테스트")
class AppLoginFlowIntegrationTest {

  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String APP_START = "/oauth2/authorization/kakao/app";
  private static final String PROVIDER_ID = "app-flow-itest";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AppLoginCodeStore appLoginCodeStore;
  @MockitoBean private RefreshTokenStore refreshTokenStore;

  @Value("${app.mobile.login-callback-url}")
  private String appCallbackUrl;

  @Value("${jwt.secret.refresh-key}")
  private String refreshSecret;

  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM users WHERE provider_id = ?", PROVIDER_ID);
  }

  private static MultiValueMap<String, String> queryOf(final MvcResult result) {
    return UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
        .build()
        .getQueryParams();
  }

  @Nested
  @DisplayName("카카오 앱 로그인 시작·실패")
  class KakaoAppLoginTest {

    @Test
    void 올바른_challenge_면_일반_로그인과_같은_카카오_인가_요청에_app_state_를_싣는다() throws Exception {
      // when
      MvcResult result =
          mockMvc
              .perform(get(APP_START).param("challenge", CHALLENGE))
              .andExpect(status().is3xxRedirection())
              .andReturn();

      // then
      assertThat(result.getResponse().getRedirectedUrl())
          .startsWith("https://kauth.kakao.com/oauth/authorize?");
      MultiValueMap<String, String> query = queryOf(result);
      String state = UriUtils.decode(query.getFirst("state"), StandardCharsets.UTF_8);
      assertThat(state).startsWith("app." + CHALLENGE + ".");
      assertThat(query).doesNotContainKey("scope");
      assertThat(query).containsKey("nonce");
      assertThat(UriUtils.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8))
          .isEqualTo("http://localhost/login/oauth2/code/kakao");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "",
          "short",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-c=",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM.extra"
        })
    void challenge_가_형식에_맞지_않으면_카카오로_보내지_않고_앱_주소로_invalid_request(final String challenge)
        throws Exception {
      mockMvc
          .perform(get(APP_START).param("challenge", challenge))
          .andExpect(status().isFound())
          .andExpect(redirectedUrl(appCallbackUrl + "?error=invalid_request"))
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void challenge_가_없어도_앱_주소로_invalid_request() throws Exception {
      mockMvc
          .perform(get(APP_START))
          .andExpect(status().isFound())
          .andExpect(redirectedUrl(appCallbackUrl + "?error=invalid_request"));
    }

    @Test
    void 카카오_동의를_취소하면_앱_주소로_cancelled() throws Exception {
      // given — 카카오로 보내기 전 세션에 저장된 앱 인가 요청과 그 state
      MvcResult authorization =
          mockMvc
              .perform(get(APP_START).param("challenge", CHALLENGE))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
      String state =
          UriUtils.decode(queryOf(authorization).getFirst("state"), StandardCharsets.UTF_8);

      // when & then
      mockMvc
          .perform(
              get("/login/oauth2/code/kakao")
                  .session(session)
                  .param("error", "access_denied")
                  .param("state", state))
          .andExpect(status().isFound())
          .andExpect(redirectedUrl(appCallbackUrl + "?error=cancelled"))
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
          .andExpect(content().string(""));
    }

    @Test
    void 일반_로그인_시작은_그대로_app_state_가_아니다() throws Exception {
      MvcResult result =
          mockMvc
              .perform(get("/oauth2/authorization/kakao"))
              .andExpect(status().is3xxRedirection())
              .andReturn();

      String state = UriUtils.decode(queryOf(result).getFirst("state"), StandardCharsets.UTF_8);
      assertThat(state).doesNotStartWith("app.").doesNotStartWith("age_range.");
    }
  }

  @Nested
  @DisplayName("로그인 전 API 는 토큰 없이 닿는다 (POST 한정)")
  class PublicAppAuthPathTest {

    @Test
    void 코드_교환은_토큰_없이_닿아_서비스가_코드를_판정한다() throws Exception {
      given(appLoginCodeStore.consume(anyString())).willReturn(Optional.empty());

      mockMvc
          .perform(
              post("/v1/auth/app/token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"code\":\"unknown\",\"verifier\":\"" + VERIFIER + "\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().string(Matchers.containsString("AUTH-007")));
    }

    @Test
    void 애플_로그인은_허용_앱이_없는_서버에서_400_AUTH_010() throws Exception {
      mockMvc
          .perform(
              post("/v1/auth/app/apple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"identityToken\":\"token\",\"rawNonce\":\"nonce\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(Matchers.containsString("AUTH-010")));
    }

    @Test
    void 앱_토큰_갱신은_토큰_없이_닿아_refresh_를_판정한다() throws Exception {
      // 만료된 앱 refresh — 보안 필터가 막았다면 AUTH-005 가 나온다.
      String expiredAppRefresh =
          Jwts.builder()
              .subject("1")
              .claim("cli", "app")
              .expiration(Date.from(Instant.now().minusSeconds(60)))
              .signWith(Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8)))
              .compact();

      mockMvc
          .perform(
              post("/v1/auth/app/reissue")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"" + expiredAppRefresh + "\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().string(Matchers.containsString("AUTH-006")));
    }

    @Test
    void 웹_refresh_는_앱_갱신에서_거절된다() throws Exception {
      // 실제 회원의 서명이 맞는 웹 refresh — 회원이 없어서가 아니라 칸이 달라서 거절되는지 본다(저장소 검증은 대역이라 통과한다).
      Long userId =
          userRepository.save(User.create("KAKAO", PROVIDER_ID, "app-flow@example.com")).getId();
      String webRefresh =
          Jwts.builder()
              .subject(String.valueOf(userId))
              .expiration(Date.from(Instant.now().plusSeconds(600)))
              .signWith(Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8)))
              .compact();

      mockMvc
          .perform(
              post("/v1/auth/app/reissue")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"" + webRefresh + "\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().string(Matchers.containsString("AUTH-005")));
    }

    @Test
    void 앱_로그아웃은_유저_토큰이_필요하다() throws Exception {
      mockMvc.perform(post("/v1/auth/app/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void 공개는_POST_한정이다() throws Exception {
      mockMvc.perform(get("/v1/auth/app/token")).andExpect(status().isUnauthorized());
    }
  }
}
