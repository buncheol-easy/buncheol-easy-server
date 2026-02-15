package buncheoleasy.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    private UserMapper userMapper;

    @Nested
    @DisplayName("insert 테스트")
    class InsertTest {

        @Test
        void User를_저장할_수_있다() {
            // given
            User user = User.create("kakao", "123456", "test@example.com");

            // when
            userMapper.insert(user);

            // then
            assertThat(user.getId()).isNotNull();
            assertThat(user.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findById 테스트")
    class FindByIdTest {

        @Test
        void ID로_User를_조회할_수_있다() {
            // given
            User user = User.create("kakao", "123456", "test@example.com");
            userMapper.insert(user);
            Long userId = user.getId();

            // when
            User foundUser = userMapper.findById(userId).orElse(null);

            // then
            assertThat(foundUser).isNotNull();
            assertThat(foundUser.getId()).isEqualTo(userId);
            assertThat(foundUser.getSocialInfo().provider().getValue()).isEqualTo("kakao");
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
    @DisplayName("findBySocialInfo 테스트")
    class FindBySocialInfoTest {

        @Test
        void provider와_providerId로_User를_조회할_수_있다() {
            // given
            User user = User.create("kakao", "social_123", "social@example.com");
            userMapper.insert(user);

            // when
            User foundUser = userMapper.findBySocialInfo("kakao", "social_123").orElse(null);

            // then
            assertThat(foundUser).isNotNull();
            assertThat(foundUser.getId()).isEqualTo(user.getId());
            assertThat(foundUser.getSocialInfo().provider().getValue()).isEqualTo("kakao");
            assertThat(foundUser.getSocialInfo().providerId()).isEqualTo("social_123");
        }

        @Test
        void 존재하지_않는_provider_providerId면_empty를_반환한다() {
            // when
            User foundUser = userMapper.findBySocialInfo("kakao", "not_found").orElse(null);

            // then
            assertThat(foundUser).isNull();
        }
    }
}
