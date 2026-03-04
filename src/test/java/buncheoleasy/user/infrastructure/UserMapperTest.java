package buncheoleasy.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@ActiveProfiles("test")
@DisplayName("UserMapper 테스트")
class UserMapperTest {

  @Autowired private UserMapper userMapper;

  @Nested
  @DisplayName("유저 저장 테스트")
  class InsertTest {

    @Test
    void User를_저장할_수_있다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when
      userMapper.insert(user);

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
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);
      Long userId = user.getId();

      // when
      User foundUser = userMapper.findById(userId).orElse(null);

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
      User foundUser = userMapper.findById(nonExistentId).orElse(null);

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
      User user = User.create("KAKAO", "social_123", "social@example.com");
      userMapper.insert(user);

      // when
      User foundUser = userMapper.findBySocialInfo("KAKAO", "social_123").orElse(null);

      // then
      assertThat(foundUser).isNotNull();
      assertThat(foundUser.getId()).isEqualTo(user.getId());
      assertThat(foundUser.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(foundUser.getSocialInfo().providerId()).isEqualTo("social_123");
    }

    @Test
    void 존재하지_않는_provider_providerId면_empty를_반환한다() {
      // when
      User foundUser = userMapper.findBySocialInfo("KAKAO", "not_found").orElse(null);

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
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);

      // when
      boolean exists = userMapper.existsById(user.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    void 존재하지_않는_ID면_false를_반환한다() {
      // when
      boolean exists = userMapper.existsById(999999L);

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
      userMapper.insert(user1);
      user1.updateNickname("닉네임충돌");
      userMapper.update(user1);

      User user2 = User.create("KAKAO", "user2", "user2@example.com");
      userMapper.insert(user2);

      // when: user2의 ID를 제외하고 "닉네임충돌" 닉네임 존재 여부 확인
      boolean exists = userMapper.existsByNicknameExcludingId("닉네임충돌", user2.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    void 자신의_ID를_제외하면_false를_반환한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);
      user.updateNickname("내닉네임");
      userMapper.update(user);

      // when: 자신의 ID를 제외하고 확인
      boolean exists = userMapper.existsByNicknameExcludingId("내닉네임", user.getId());

      // then
      assertThat(exists).isFalse();
    }

    @Test
    void 해당_닉네임이_없으면_false를_반환한다() {
      // when
      boolean exists = userMapper.existsByNicknameExcludingId("없는닉네임", 999999L);

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("유저 수정 테스트")
  class UpdateTest {

    @Test
    void User_정보를_업데이트할_수_있다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);
      String newNickname = "새닉네임";
      String newPhoneNumber = "01012345678";
      user.updateNickname(newNickname);
      user.updatePhoneNumber(newPhoneNumber);

      // when
      userMapper.update(user);

      // then
      User updated = userMapper.findById(user.getId()).orElseThrow();
      assertThat(updated.getNickname().value()).isEqualTo(newNickname);
      assertThat(updated.getPhoneNumber().value()).isEqualTo(newPhoneNumber);
      assertThat(updated.isProfileCompleted()).isTrue();
    }
  }

  @Nested
  @DisplayName("회원 탈퇴 테스트")
  class UpdateDeletedAtTest {

    @Test
    void 회원_탈퇴_시_deletedAt이_설정되고_조회되지_않는다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);
      Long userId = user.getId();

      user.withdraw();

      // when
      userMapper.updateDeletedAt(userId, user.getDeletedAt());

      // then
      User found = userMapper.findById(userId).orElse(null);
      assertThat(found).isNull();
    }

    @Test
    void 탈퇴_후_existsById도_false를_반환한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      userMapper.insert(user);
      Long userId = user.getId();

      user.withdraw();
      userMapper.updateDeletedAt(userId, user.getDeletedAt());

      // when
      boolean exists = userMapper.existsById(userId);

      // then
      assertThat(exists).isFalse();
    }
  }
}
