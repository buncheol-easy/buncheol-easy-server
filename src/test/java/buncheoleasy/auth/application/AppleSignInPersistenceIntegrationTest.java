package buncheoleasy.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.C2cHostQualification;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 애플 가입이 실제로 저장되는 모양 — provider 칸(VARCHAR(20))·점이 든 sub·개최 자격. 서비스가 연 트랜잭션의 커밋 결과로 본다. */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("애플 가입 영속 통합 테스트")
class AppleSignInPersistenceIntegrationTest {

  private static final String SUBJECT = "001234.0a1b2c3d4e5f60718293a4b5c6d7e8f9.0123";

  @Autowired private SocialLoginService socialLoginService;
  @Autowired private UserDomainService userDomainService;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM users WHERE provider_id = ?", SUBJECT);
  }

  private static SocialLoginCommand appleCommand(final String name) {
    return new SocialLoginCommand(
        "APPLE",
        SUBJECT,
        "relay@privaterelay.appleid.com",
        name,
        null,
        null,
        false,
        List.of());
  }

  @Test
  void 애플_가입은_APPLE_과_sub_로_저장되고_연령대_전화번호가_없다() {
    // when
    LoginAccount account = socialLoginService.signIn(appleCommand("홍길동"));

    // then
    assertThat(account.newAccount()).isTrue();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT provider, provider_id, name, phone_number, age_range, profile_completed"
                + " FROM users WHERE id = ?",
            account.userId());
    assertThat(row.get("provider")).isEqualTo("APPLE");
    assertThat(row.get("provider_id")).isEqualTo(SUBJECT);
    assertThat(row.get("name")).isEqualTo("홍길동");
    assertThat(row.get("phone_number")).isNull();
    assertThat(row.get("age_range")).isNull();
  }

  @Test
  void 두_번째_애플_로그인은_같은_회원이고_이름을_덮지_않는다() {
    // given
    Long userId = socialLoginService.signIn(appleCommand("홍길동")).userId();

    // when — 애플은 두 번째부터 이름을 주지 않지만, 앱이 다른 값을 보내도 기존 값을 지킨다
    LoginAccount again = socialLoginService.signIn(appleCommand("다른이름"));

    // then
    assertThat(again).isEqualTo(new LoginAccount(userId, false));
    User user = userRepository.findBySocialInfo(SocialInfo.of("APPLE", SUBJECT)).orElseThrow();
    assertThat(user.getName()).isEqualTo("홍길동");
    assertThat(user.getSocialInfo().provider()).isEqualTo(SocialProvider.APPLE);
  }

  @Test
  void 첫_로그인이_동시에_여러_번_와도_500_없이_한_회원으로_모인다() throws Exception {
    // 앱 재시도·두 번 누름. 진 요청은 유니크 인덱스 충돌 뒤 다시 조회해 먼저 만든 회원으로 로그인한다.
    int threads = 6;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<LoginAccount>> results = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return socialLoginService.signIn(appleCommand(null));
                }));
      }
      start.countDown();

      Set<Long> userIds = new HashSet<>();
      int newAccounts = 0;
      for (Future<LoginAccount> result : results) {
        LoginAccount account = result.get();
        userIds.add(account.userId());
        newAccounts += account.newAccount() ? 1 : 0;
      }
      assertThat(userIds).hasSize(1);
      assertThat(newAccounts).isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM users WHERE provider_id = ?", Integer.class, SUBJECT))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void 애플_가입자는_카카오_가입자와_같은_순서로_C2C_개최_자격을_갖춘다() {
    // given — 애플은 전화번호를 주지 않는다. 가입 정보 입력 전이면 전화번호 사유로 막힌다.
    Long userId = socialLoginService.signIn(appleCommand(null)).userId();
    assertThat(userDomainService.evaluateC2cHostQualification(userId))
        .isEqualTo(C2cHostQualification.PHONE_REQUIRED);

    // when — 전화번호를 넣으면 다음은 「만 19세 이상」 체크다(연령대는 보지 않는다).
    userDomainService.updateProfile(userId, "애플회원", "01012345678", null, null);
    assertThat(userDomainService.evaluateC2cHostQualification(userId))
        .isEqualTo(C2cHostQualification.ADULT_CONFIRM_REQUIRED);

    // then — 체크를 남기면 개최할 수 있다.
    userDomainService.confirmHostAdult(userId);
    assertThat(userDomainService.evaluateC2cHostQualification(userId))
        .isEqualTo(C2cHostQualification.QUALIFIED);
  }
}
