package buncheoleasy.support;

import buncheoleasy.notification.infrastructure.AligoAlimtalkClient;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 MySQL 8 에서 도는 테스트의 기반. H2 는 InnoDB 의 잠금·데드락과 {@code UTC_TIMESTAMP()}, UPDATE 대상 표를 서브쿼리에서
 * 읽는 제약(error 1093)을 재현하지 못한다. 운영과 같은 {@code schema.sql} 을 적재한다.
 *
 * <p>컨테이너 하나를 테스트 실행 동안 공유하고(Ryuk 가 JVM 종료 때 치운다), 하위 클래스는 이 설정을 그대로 물려받는다. 목 빈 조합을
 * 클래스마다 바꾸면 컨텍스트가 늘어나 캐시 상한(build.gradle)에서 밀려나고, 밀려날 때마다 이 데이터소스를 다시 띄운다.
 *
 * <p>{@code @Transactional} 을 쓰지 않는다 — 잠금과 커밋 경계를 보려고 실제로 커밋하고, 매 테스트 앞에서 표를 비운다. Docker 가
 * 없으면 건너뛴다(CI 는 Docker 가 있다).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("mysql")
public abstract class MySqlContainerTestSupport {

  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0")
          // performance_schema 의 잠금 표를 읽으려면 root 여야 한다.
          .withUsername("root")
          .withPassword("test")
          .withDatabaseName("buncheoleasy_test")
          .withUrlParam("serverTimezone", "UTC")
          .withUrlParam("characterEncoding", "UTF-8");

  @Autowired protected JdbcTemplate jdbcTemplate;

  // 커밋 뒤 알림 리스너가 외부로 나가지 않게 막는다. 모든 MySQL 테스트가 이 조합 하나를 공유한다.
  @MockitoBean protected AligoAlimtalkClient aligoAlimtalkClient;
  @MockitoBean protected SlackWebhookClient slackWebhookClient;

  @DynamicPropertySource
  static void mysqlProperties(final DynamicPropertyRegistry registry) {
    MYSQL.start();
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
  }

  /** 앞 테스트가 남긴 행과 커밋 뒤 비동기로 늦게 쓰인 행을 함께 지운다. 자동 증가 값도 처음으로 돌린다. */
  @BeforeEach
  void truncateAllTables() {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              List<String> tables = new ArrayList<>();
              try (Statement statement = connection.createStatement();
                  ResultSet rs =
                      statement.executeQuery(
                          "SELECT table_name FROM information_schema.tables"
                              + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
                while (rs.next()) {
                  tables.add(rs.getString(1));
                }
              }
              try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                  statement.execute("TRUNCATE TABLE `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
              }
              return null;
            });
  }
}
