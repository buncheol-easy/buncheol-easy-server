package buncheoleasy.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.C2cHostQualification;
import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.UserRepository;
import java.util.List;
import java.util.Map;
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
  void 애플_가입자는_전화번호를_넣어도_연령대가_없어_C2C_개최_자격이_없다() {
    // given
    Long userId = socialLoginService.signIn(appleCommand(null)).userId();

    // when
    userDomainService.updateProfile(userId, "애플회원", "01012345678", null, null);

    // then
    assertThat(userDomainService.evaluateC2cHostQualification(userId))
        .isEqualTo(C2cHostQualification.AGE_UNVERIFIED);
  }
}
