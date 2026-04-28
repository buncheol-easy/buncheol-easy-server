package buncheoleasy.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaUserRepositoryAdapter 테스트")
class JpaUserRepositoryAdapterTest {

  @Autowired private UserRepository userRepository;

  @PersistenceContext private EntityManager em;

  private User persistAndDetach(User user) {
    userRepository.save(user);
    em.flush();
    em.clear();
    return user;
  }

  @Nested
  @DisplayName("유저 저장 테스트")
  class SaveTest {

    @Test
    void User를_저장하면_ID가_할당된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when
      userRepository.save(user);
      em.flush();

      // then
      assertThat(user.getId()).isNotNull();
      assertThat(user.getId()).isPositive();
    }
  }

  @Nested
  @DisplayName("ID로 유저 조회 테스트")
  class FindByIdTest {

    @Test
    void ID로_User를_조회할_수_있다() {
      // given
      User user = persistAndDetach(User.create("KAKAO", "123456", "test@example.com"));
      Long userId = user.getId();

      // when
      User foundUser = userRepository.findById(userId).orElse(null);

      // then
      assertThat(foundUser).isNotNull();
      assertThat(foundUser.getId()).isEqualTo(userId);
      assertThat(foundUser.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(foundUser.getSocialInfo().providerId()).isEqualTo("123456");
      assertThat(foundUser.getEmail().value()).isEqualTo("test@example.com");
      assertThat(foundUser.getNickname().value()).startsWith("Guest");
      assertThat(foundUser.getPhoneNumber()).isNull();
      assertThat(foundUser.getDeletedAt()).isNull();
    }

    @Test
    void 존재하지_않는_ID로_조회하면_empty를_반환한다() {
      // given
      Long nonExistentId = 999999L;

      // when
      User foundUser = userRepository.findById(nonExistentId).orElse(null);

      // then
      assertThat(foundUser).isNull();
    }
  }

  @Nested
  @DisplayName("소셜 정보로 유저 조회 테스트")
  class FindBySocialInfoTest {

    @Test
    void provider와_providerId로_User를_조회할_수_있다() {
      // given
      User user = persistAndDetach(User.create("KAKAO", "social_123", "social@example.com"));

      // when
      User foundUser =
          userRepository.findBySocialInfo(SocialInfo.of("KAKAO", "social_123")).orElse(null);

      // then
      assertThat(foundUser).isNotNull();
      assertThat(foundUser.getId()).isEqualTo(user.getId());
      assertThat(foundUser.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(foundUser.getSocialInfo().providerId()).isEqualTo("social_123");
    }

    @Test
    void 존재하지_않는_provider_providerId면_empty를_반환한다() {
      // when
      User foundUser =
          userRepository.findBySocialInfo(SocialInfo.of("KAKAO", "not_found")).orElse(null);

      // then
      assertThat(foundUser).isNull();
    }
  }

  @Nested
  @DisplayName("유저 존재 여부 확인 테스트")
  class ExistsByIdTest {

    @Test
    void 존재하는_ID면_true를_반환한다() {
      // given
      User user = persistAndDetach(User.create("KAKAO", "123456", "test@example.com"));

      // when
      boolean exists = userRepository.existsById(user.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    void 존재하지_않는_ID면_false를_반환한다() {
      // when
      boolean exists = userRepository.existsById(999999L);

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("중복 닉네임 검사 테스트")
  class ExistsByNicknameExcludingIdTest {

    @Test
    void 다른_유저가_동일한_닉네임을_사용하면_true를_반환한다() {
      // given
      User user1 = User.create("KAKAO", "user1", "user1@example.com");
      user1.updateNickname("닉네임충돌");
      userRepository.save(user1);

      User user2 = User.create("KAKAO", "user2", "user2@example.com");
      userRepository.save(user2);
      em.flush();
      em.clear();

      // when: user2의 ID를 제외하고 "닉네임충돌" 닉네임 존재 여부 확인
      boolean exists = userRepository.existsByNicknameExcludingId("닉네임충돌", user2.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    void 자신의_ID를_제외하면_false를_반환한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateNickname("내닉네임");
      User saved = persistAndDetach(user);

      // when: 자신의 ID를 제외하고 확인
      boolean exists = userRepository.existsByNicknameExcludingId("내닉네임", saved.getId());

      // then
      assertThat(exists).isFalse();
    }

    @Test
    void 해당_닉네임이_없으면_false를_반환한다() {
      // when
      boolean exists = userRepository.existsByNicknameExcludingId("없는닉네임", 999999L);

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("유저 수정 테스트")
  class UpdateTest {

    @Test
    void managed_엔티티의_도메인_메서드_호출_시_더티체크로_DB가_갱신된다() {
      // given
      User saved = persistAndDetach(User.create("KAKAO", "123456", "test@example.com"));
      String newNickname = "새닉네임";
      String newPhoneNumber = "01012345678";

      // when: managed 상태로 다시 로드 후 도메인 메서드만 호출 → flush 시 dirty UPDATE 가 발생해야 한다
      User managed = userRepository.findById(saved.getId()).orElseThrow();
      managed.updateNickname(newNickname);
      managed.updatePhoneNumber(newPhoneNumber);
      em.flush();
      em.clear();

      // then
      User updated = userRepository.findById(saved.getId()).orElseThrow();
      assertThat(updated.getNickname().value()).isEqualTo(newNickname);
      assertThat(updated.getPhoneNumber().value()).isEqualTo(newPhoneNumber);
      assertThat(updated.isProfileCompleted()).isTrue();
    }
  }

  @Nested
  @DisplayName("회원 탈퇴 테스트")
  class WithdrawTest {

    @Test
    void 회원_탈퇴_시_deletedAt이_설정되고_조회되지_않는다() {
      // given
      User saved = persistAndDetach(User.create("KAKAO", "123456", "test@example.com"));
      Long userId = saved.getId();

      // when: managed 엔티티에 withdraw() 호출 → flush 시 dirty UPDATE 로 deletedAt 반영
      User managed = userRepository.findById(userId).orElseThrow();
      managed.withdraw();
      em.flush();
      em.clear();

      // then: @SQLRestriction 으로 자동 제외되어 조회되지 않는다
      User found = userRepository.findById(userId).orElse(null);
      assertThat(found).isNull();
    }

    @Test
    void 탈퇴_후_existsById도_false를_반환한다() {
      // given
      User saved = persistAndDetach(User.create("KAKAO", "123456", "test@example.com"));
      Long userId = saved.getId();

      User managed = userRepository.findById(userId).orElseThrow();
      managed.withdraw();
      em.flush();
      em.clear();

      // when
      boolean exists = userRepository.existsById(userId);

      // then
      assertThat(exists).isFalse();
    }
  }
}
