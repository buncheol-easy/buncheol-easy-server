package buncheoleasy.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String[] ALLOWED_METHODS = {
    "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
  };

  @Value("${app.cors.allowed-origin-patterns}")
  private String[] allowedOriginPatterns;

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
