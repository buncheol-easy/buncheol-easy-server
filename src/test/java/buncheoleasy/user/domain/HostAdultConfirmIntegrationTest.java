package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import buncheoleasy.user.domain.serviceterm.UserServiceTermRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 「만 19세 이상」 체크의 실제 영속 검증. 저장은 서비스 트랜잭션 없이 저장소 호출마다 커밋되므로(유니크 위반을 넘기기 위해), Mockito 로는 detached 수정이
 * 유실되거나 위반이 그대로 터지는 경우를 못 잡는다. 그래서 테스트 트랜잭션 없이 커밋 결과를 JDBC 로 다시 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("개최 만 19세 본인 체크 영속 통합 테스트")
class HostAdultConfirmIntegrationTest {

  private static final String PROVIDER_ID = "host-adult-itest";
  private static final String TAG = UserServiceTerm.HOST_ADULT_CONFIRM_TAG;
  private static final Instant FIRST_CONFIRMED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private UserDomainService userDomainService;

  @Autowired private UserServiceTermRepository userServiceTermRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private Long userId;

  @BeforeEach
  void setUp() {
    userId =
        userDomainService
            .getOrCreateBySocialLogin(
                SocialInfo.of("KAKAO", PROVIDER_ID),
                "itest@example.com",
                null,
                "01012345678",
                null,
                false)
            .user()
            .getId();
  }

  @AfterEach
  void tearDown() {
    // 테스트 트랜잭션이 없어 롤백되지 않는다. 체크 행은 FK ON DELETE CASCADE 로 함께 지워진다.
    jdbcTemplate.update("DELETE FROM users WHERE provider_id = ?", PROVIDER_ID);
  }

  private int rowCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_service_terms WHERE user_id = ? AND tag = ?",
        Integer.class,
        userId,
        TAG);
  }

  private boolean agreed() {
    return jdbcTemplate.queryForObject(
        "SELECT agreed FROM user_service_terms WHERE user_id = ? AND tag = ?",
        Boolean.class,
        userId,
        TAG);
  }

  private Instant agreedAt() {
    return jdbcTemplate
        .queryForObject(
            "SELECT agreed_at FROM user_service_terms WHERE user_id = ? AND tag = ?",
            Timestamp.class,
            userId,
            TAG)
        .toInstant();
  }

  private void moveAgreedAtToPast() {
    jdbcTemplate.update(
        "UPDATE user_service_terms SET agreed_at = ? WHERE user_id = ? AND tag = ?",
        Timestamp.from(FIRST_CONFIRMED_AT),
        userId,
        TAG);
  }

  @Test
  void 체크가_DB에_커밋된다() {
    userDomainService.confirmHostAdult(userId);

    assertThat(rowCount()).isEqualTo(1);
    assertThat(agreed()).isTrue();
    assertThat(agreedAt()).isNotNull();
  }

  @Test
  void 다시_불러도_첫_체크_시각이_유지되고_행이_늘지_않는다() {
    userDomainService.confirmHostAdult(userId);
    moveAgreedAtToPast();

    userDomainService.confirmHostAdult(userId);

    assertThat(rowCount()).isEqualTo(1);
    assertThat(agreedAt()).isEqualTo(FIRST_CONFIRMED_AT);
  }

  @Test
  void 동의_안_함_행이_있으면_체크로_바뀌어_DB에_커밋된다() {
    jdbcTemplate.update(
        "INSERT INTO user_service_terms (user_id, tag, agreed, agreed_at) VALUES (?, ?, FALSE, NULL)",
        userId,
        TAG);

    userDomainService.confirmHostAdult(userId);

    assertThat(rowCount()).isEqualTo(1);
    assertThat(agreed()).isTrue();
    assertThat(agreedAt()).isNotNull();
  }

  @Test
  void 사전_조회를_지나_유니크에_걸려도_예외_없이_먼저_들어간_행을_남긴다() {
    userServiceTermRepository.saveIfAbsent(
        UserServiceTerm.of(userId, TAG, true, FIRST_CONFIRMED_AT));

    assertThatCode(
            () ->
                userServiceTermRepository.saveIfAbsent(
                    UserServiceTerm.of(userId, TAG, true, Instant.parse("2026-02-02T00:00:00Z"))))
        .doesNotThrowAnyException();

    assertThat(rowCount()).isEqualTo(1);
    assertThat(agreedAt()).isEqualTo(FIRST_CONFIRMED_AT);
  }

  @Test
  void 유니크_말고_다른_무결성_위반은_삼키지_않는다() {
    Long nobody = -1L;

    assertThatThrownBy(
            () ->
                userServiceTermRepository.saveIfAbsent(
                    UserServiceTerm.of(nobody, TAG, true, FIRST_CONFIRMED_AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 동시에_여러_번_눌러도_모두_성공하고_한_행만_남는다() throws Exception {
    int callers = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(callers);
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  userDomainService.confirmHostAdult(userId);
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> result : results) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(rowCount()).isEqualTo(1);
    assertThat(agreed()).isTrue();
  }

  @Test
  void 재로그인_약관_갱신은_체크_행을_건드리지_않는다() {
    userDomainService.confirmHostAdult(userId);
    moveAgreedAtToPast();

    userDomainService.updateServiceTermAgreements(
        userId,
        List.of(new ServiceTermAgreement("service_terms", true, Instant.now())),
        "marketing");

    assertThat(agreed()).isTrue();
    assertThat(agreedAt()).isEqualTo(FIRST_CONFIRMED_AT);
  }
}
