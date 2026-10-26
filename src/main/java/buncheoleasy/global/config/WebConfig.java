package buncheoleasy.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String[] ALLOWED_METHODS = {
    "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
  };

  /**
   * 요청 내내 EntityManager 를 열어 두는(OSIV) 인터셉터를 빼는 경로. AI 개최글은 요청 스레드가 엔진을 최대 25초 기다리는데, OSIV 면 그 앞의 조회
   * 트랜잭션이 잡은 DB 커넥션을 요청이 끝날 때까지 쥔다 — 동시 요청 수만큼 커넥션 풀이 비어 DB 를 쓰는 다른 API 가 같이 멈춘다.
   */
  private static final String[] NO_OPEN_IN_VIEW_PATHS = {"/v1/ai/**"};

  @Value("${app.cors.allowed-origin-patterns}")
  private String[] allowedOriginPatterns;

  private final EntityManagerFactory entityManagerFactory;

  public WebConfig(final EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  /**
   * Boot 의 OSIV 자동 등록({@code spring.jpa.open-in-view}, 기본 켜짐)을 끄고 같은 인터셉터를 직접 건다. 등록 방식(전 경로 웹 요청 인터셉터)은 Boot
   * 와 같고 {@link #NO_OPEN_IN_VIEW_PATHS} 만 뺀다 — 다른 API 는 지금처럼 화면 조립까지 지연 로딩이 된다.
   */
  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    OpenEntityManagerInViewInterceptor openInView = new OpenEntityManagerInViewInterceptor();
    openInView.setEntityManagerFactory(entityManagerFactory);
    registry.addWebRequestInterceptor(openInView).excludePathPatterns(NO_OPEN_IN_VIEW_PATHS);
  }

  @Override
  public void addCorsMappings(final CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns(allowedOriginPatterns)
        .allowedMethods(ALLOWED_METHODS)
        .allowCredentials(true)
        .allowedHeaders("*");
  }

  @Override
  public void addViewControllers(final ViewControllerRegistry registry) {
    registry.addViewController("/v1/api-docs").setViewName("forward:/v1/api-docs/scalar.html");
    registry.addViewController("/v1/api-docs/").setViewName("forward:/v1/api-docs/scalar.html");
  }
}
