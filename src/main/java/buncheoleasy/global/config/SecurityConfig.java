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
    "/v1/payments/success",
    "/v1/payments/fail",
    "/payment/**",
    "/v1/api-docs/**"
  };

  private static final String[] OAUTH_PATHS = {"/oauth2/**", "/login/oauth2/**"};

  /** 사용자 컨텍스트가 필요 없는 공개 조회 API (GET 한정). */
  private static final String[] PUBLIC_GET_PATHS = {
    "/v1/groups", "/v1/groups/*/members", "/v1/buncheols"
  };

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
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll() // GET 한정 공개 조회 API (비로그인 둘러보기)
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll() // OPTIONS 프리플라이트 요청
                    .anyRequest()
                    .authenticated() // 그 외 모든 요청은 인증 필요
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
}
