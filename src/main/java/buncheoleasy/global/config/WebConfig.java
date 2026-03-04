package buncheoleasy.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String[] ALLOWED_METHODS = {
    "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
  };

  @Override
  public void addCorsMappings(final CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns("http://localhost:*")
        .allowedMethods(ALLOWED_METHODS)
        .allowCredentials(true)
        .allowedHeaders("*");
  }
}
