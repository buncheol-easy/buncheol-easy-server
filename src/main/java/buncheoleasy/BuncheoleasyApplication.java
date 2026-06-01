package buncheoleasy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class BuncheoleasyApplication {
  public static void main(String[] args) {
    SpringApplication.run(BuncheoleasyApplication.class, args);
  }
}
