package buncheoleasy.global.config;

import buncheoleasy.auth.infrastructure.oauth.OAuth2LoginFailureHandler;
import buncheoleasy.auth.infrastructure.oauth.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/", "/index.html",
            "/favicon.ico",
            "/error"
    };

    private static final String[] OAUTH_PATHS = {
            "/oauth2/**",
            "/login/oauth2/**"
    };

    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 설정
                .cors(Customizer.withDefaults()) // CORS 설정
                // Oauth2Login은 기본적으로 세션 사용, JWT 기반 인증하기 위해 비활성화
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth // 요청별 접근 규칙
                        // 정적 리소스
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // OAuth2 로그인 진입/콜백 경로
                        .requestMatchers(OAUTH_PATHS).permitAll()
                        // OPTIONS 프리플라이트 요청
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .requestCache(cache -> cache.disable()) // 이전 요청 저장/재시도 흐름 제거

                /**
                 * OAuth2 Login 활성화 - Spring Security가 아래 항목을 순서대로 자동 처리
                 *   1. authorization endpoint로 리다이렉트
                 *   2. code 수신
                 *   3. code -> access_token & id_token 획득
                 *   4. Authentication 생성 후 SecurityContext에 저장
                 */
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler) // 로그인 성공 시 동작
                        .failureHandler(oAuth2LoginFailureHandler) // 로그인 실패 시 동작
                )
                .httpBasic(httpBasic -> httpBasic.disable()) // 기본 HTTP Basic 비활성화
                .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}
