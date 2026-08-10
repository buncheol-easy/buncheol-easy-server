package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 연령대 저장 경로의 실제 영속 검증. 기존 회원 갱신은 {@code getOrCreateBySocialLogin} 의 {@code @Transactional} 프록시 + dirty
 * checking 에 전적으로 의존하는데, Mockito 단위 테스트는 setter 호출만 보므로 애노테이션이 빠져도(= detached 엔티티로 변경 유실) 통과해 버린다.
 * 그래서 이 테스트는 의도적으로 테스트 트랜잭션 없이 서비스가 스스로 연 트랜잭션의 커밋 결과를 재조회로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserDomainService 연령대 영속 통합 테스트")
class UserAgeRangeIntegrationTest {

  private static final String PROVIDER_ID = "age-range-itest";
  private static final SocialInfo SOCIAL_INFO = SocialInfo.of("KAKAO", PROVIDER_ID);

  @Autowired private UserDomainService userDomainService;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    // 테스트 트랜잭션이 없어 롤백되지 않으므로 커밋된 행을 직접 정리한다.
    jdbcTemplate.update("DELETE FROM users WHERE provider_id = ?", PROVIDER_ID);
  }

  private User reload() {
    return userRepository.findBySocialInfo(SOCIAL_INFO).orElseThrow();
  }

  @Test
  void 기존_회원의_재로그인_연령대_갱신이_DB에_커밋된다() {
    // given: 연령대 없이 가입된 기존 회원
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, null, false);
    assertThat(reload().getAgeRange()).isNull();

    // when: 재로그인에서 연령대 동의 값이 내려옴
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, "20~29", false);

    // then: 새 영속성 컨텍스트에서 재조회해도 값이 남아 있다
    assertThat(reload().getAgeRange()).isEqualTo("20~29");
  }

  @Test
  void 동의_철회가_확인된_재로그인은_저장된_연령대를_DB에서_파기한다() {
    // given
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, "20~29", false);
    assertThat(reload().getAgeRange()).isEqualTo("20~29");

    // when
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, null, true);

    // then
    assertThat(reload().getAgeRange()).isNull();
  }

  @Test
  void 신호가_없는_재로그인은_저장된_연령대를_유지한다() {
    // given
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, "20~29", false);

    // when: 보강 조회 실패 등으로 연령대 신호가 전혀 없는 재로그인
    userDomainService.getOrCreateBySocialLogin(
        SOCIAL_INFO, "itest@example.com", null, null, null, false);

    // then
    assertThat(reload().getAgeRange()).isEqualTo("20~29");
  }
}
