package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 카카오가 이메일을 주지 않은 소셜 로그인의 실제 영속 결과. 거절된 가입이 회원 행을 남기지 않는지, 기존 회원의 저장된 이메일이 지워지지 않는지를 서비스가 스스로 연
 * 트랜잭션의 커밋 결과로 확인한다(테스트 트랜잭션 없음).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserDomainService 소셜 로그인 이메일 영속 통합 테스트")
class UserSocialLoginEmailIntegrationTest {

  private static final String PROVIDER_ID = "social-email-itest";
  private static final SocialInfo SOCIAL_INFO = SocialInfo.of("KAKAO", PROVIDER_ID);

  @Autowired private UserDomainService userDomainService;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM users WHERE provider_id = ?", PROVIDER_ID);
  }

  private int countUsers() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE provider_id = ?", Integer.class, PROVIDER_ID);
  }

  @Test
  void 새_회원인데_이메일이_없으면_회원_행이_생기지_않는다() {
    assertThatThrownBy(
            () ->
                userDomainService.getOrCreateBySocialLogin(
                    SOCIAL_INFO, null, "김실명", "01012345678", "20~29", false))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);

    assertThat(countUsers()).isZero();
  }

  @Test
  void 기존_회원이_이메일_없이_다시_로그인해도_저장된_이메일이_남는다() {
    // given
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, null, false);

    // when
    SocialLoginUser loginUser =
        userDomainService.getOrCreateBySocialLogin(SOCIAL_INFO, null, null, null, null, false);

    // then
    assertThat(loginUser.newAccount()).isFalse();
    assertThat(countUsers()).isEqualTo(1);
    assertThat(userRepository.findBySocialInfo(SOCIAL_INFO).orElseThrow().getEmail().value())
        .isEqualTo("itest@example.com");
  }
}
