package buncheoleasy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.dto.request.UpdateUserProfileRequest;
import buncheoleasy.user.dto.response.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Nested
    @DisplayName("회원 탈퇴 테스트")
    class WithdrawTest {

        @Test
        void 정상적으로_탈퇴하고_리프레시_토큰을_삭제한다() {
            // given
            Long userId = 1L;

            // when
            userService.withdraw(userId);

            // then
            then(userDomainService).should().withdraw(userId);
            then(refreshTokenStore).should().delete(userId);
        }

        @Test
        void 리프레시_토큰_삭제_실패해도_탈퇴는_정상_처리된다() {
            // given
            Long userId = 1L;
            doThrow(new RuntimeException("Redis 연결 실패"))
                    .when(refreshTokenStore).delete(userId);

            // when & then: 예외가 전파되지 않아야 함
            userService.withdraw(userId);

            then(userDomainService).should().withdraw(userId);
        }
    }

    @Nested
    @DisplayName("User 프로필 업데이트 테스트")
    class UpdateProfileTest {

        @Test
        void 프로필을_정상적으로_업데이트한다() {
            // given
            Long userId = 1L;
            UpdateUserProfileRequest request = new UpdateUserProfileRequest("새닉네임", "01012345678");

            // when
            userService.updateProfile(userId, request);

            // then
            then(userDomainService).should().updateProfile(userId, "새닉네임", "01012345678");
        }
    }

    @Nested
    @DisplayName("User 프로필 조회 테스트")
    class GetUserProfileTest {

        @Test
        void 프로필이_완료된_유저의_정보를_조회할_수_있다() {
            // given
            Long userId = 1L;
            User user = User.create("KAKAO", "123456", "test@example.com");
            user.updatePhoneNumber("01012345678");
            user.updateNickname("테스트닉");
            given(userDomainService.getUser(userId)).willReturn(user);

            // when
            UserProfileResponse response = userService.getUserProfile(userId);

            // then
            assertThat(response.provider()).isEqualTo("KAKAO");
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.nickname()).isEqualTo("테스트닉");
            assertThat(response.phoneNumber()).isEqualTo("01012345678");
        }

        @Test
        void 프로필이_완료되지_않은_유저를_조회하면_예외가_발생한다() {
            // given
            Long userId = 1L;
            User user = User.create("KAKAO", "123456", "test@example.com");
            // profileCompleted = false (전화번호 미설정)
            given(userDomainService.getUser(userId)).willReturn(user);

            // when & then
            assertThatThrownBy(() -> userService.getUserProfile(userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
        }
    }
}
