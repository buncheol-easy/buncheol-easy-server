package buncheoleasy.global.config;

import buncheoleasy.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import buncheoleasy.auth.infrastructure.jwt.JwtAuthenticationFilter;
import buncheoleasy.auth.infrastructure.oauth.OAuth2LoginFailureHandler;
import buncheoleasy.auth.infrastructure.oauth.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/",
    "/index.html",
    "/favicon.ico",
    "/error",
    "/actuator/health",
    "/v1/auth/reissue-token",
    "/v1/api-docs/**"
  };

  private static final String[] OAUTH_PATHS = {"/oauth2/**", "/login/oauth2/**"};

  /** 사용자 컨텍스트가 필요 없는 공개 조회 API (GET 한정). */
  private static final String[] PUBLIC_GET_PATHS = {
    "/v1/groups",
    "/v1/groups/popular",
    "/v1/groups/members",
    "/v1/groups/*/members",
    "/v1/buncheols",
    "/v1/buncheols/*",
    "/v1/search-keywords/recent",
    "/v1/inbox",
    "/v1/inbox/*",
    "/v1/banners"
  };

  /**
   * {@link #PUBLIC_GET_PATHS} 의 단일 세그먼트 매처 (예: {@code /v1/buncheols/{id}}) 는 {@code /me} 같은 인증 필요
   * 경로까지 함께 매칭한다. 매칭 우선순위가 더 높은 위치에서 별도로 authenticated 를 강제해 공개로 새지 않도록 한다.
   */
  private static final String[] AUTH_REQUIRED_GET_PATHS = {"/v1/buncheols/me"};

  /**
   * 관리자(ROLE_ADMIN) 전용 경로. role 은 JWT access token 의 role claim 에서 온다({@code
   * JwtAuthenticationFilter}). 공지 작성/고정은 기존 경로를 유지한 채 관리자 전용으로 잠근다.
   *
   * <p>유저 API 는 authenticated 가 아니라 {@code hasRole("USER")} 로 잠근다 — 관리자 계정(admins)과 유저(users)의 id
   * 공간이 겹칠 수 있어, 관리자 토큰이 같은 id 의 유저로 위장해 유저 API 를 호출하는 것을 role 로 차단한다.
   */
  private static final String[] ADMIN_PATHS = {"/v1/admin/**", "/v1/notices/*/pin"};

  /** 관리자 ID/PW 로그인. ADMIN_PATHS(/v1/admin/**)보다 먼저 permitAll 로 열어야 한다. */
  private static final String ADMIN_LOGIN_PATH = "/v1/admin/auth/login";

  /** 의견 보내기(POST 한정 공개). 로그인 실패·가입 이탈처럼 <b>로그인할 수 없는 상태의 의견</b>이 가장 받고 싶은 종류라 비로그인도 허용한다. */
  private static final String FEEDBACK_PATH = "/v1/feedbacks";

  private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
  private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        /** CSRF 설정 */
        .csrf(csrf -> csrf.disable())
        /** CORS 설정 */
        .cors(Customizer.withDefaults())
        /** Oauth2Login은 기본적으로 세션 사용, JWT 기반 인증하기 위해 비활성화 */
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        /** 요청별 접근 규칙 */
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_PATHS)
                    .permitAll() // 메서드 무관 공개 (정적 리소스, 외부 콜백 등)
                    .requestMatchers(OAUTH_PATHS)
                    .permitAll() // OAuth2 로그인 진입/콜백 경로
                    .requestMatchers(HttpMethod.POST, ADMIN_LOGIN_PATH)
                    .permitAll() // 관리자 ID/PW 로그인 (ADMIN_PATHS 매처보다 먼저)
                    .requestMatchers(ADMIN_PATHS)
                    .hasRole("ADMIN") // 관리자 전용 API
                    .requestMatchers(HttpMethod.POST, "/v1/notices")
                    .hasRole("ADMIN") // 공지 작성 — 관리자 전용
                    .requestMatchers(HttpMethod.POST, FEEDBACK_PATH)
                    .permitAll() // 의견 보내기 — 로그인이 안 돼서 남기는 의견도 받는다(도배는 서버 rate limit 으로 방어)
                    .requestMatchers(HttpMethod.GET, AUTH_REQUIRED_GET_PATHS)
                    .hasRole("USER") // PUBLIC_GET_PATHS 단일 세그먼트 매처보다 우선 적용
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll() // GET 한정 공개 조회 API (비로그인 둘러보기)
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll() // OPTIONS 프리플라이트 요청
                    .anyRequest()
                    .hasRole("USER") // 그 외 모든 요청은 유저 토큰 필요 (관리자 토큰 불가)
            )
        /** 필터 처리 중 발생한 예외 핸들링 */
        .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
        /** 이전 요청 저장/재시도 흐름 제거 */
        .requestCache(cache -> cache.disable())

        /**
         * OAuth2 Login - Spring Security가 아래 항목을 순서대로 처리 1. authorization endpoint로 리다이렉트 2. code
         * 수신 3. code -> access_token & id_token 획득 4. Authentication 생성 후 SecurityContext에 저장
         */
        .oauth2Login(
            oauth2 ->
                oauth2
                    .successHandler(oAuth2LoginSuccessHandler) // 로그인 성공 시 동작
                    .failureHandler(oAuth2LoginFailureHandler) // 로그인 실패 시 동작
            )
        /** 기본 HTTP Basic 비활성화 */
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable())
        /** JWT 인증 필터 */
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /** 관리자 ID/PW 비밀번호 해싱용. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
