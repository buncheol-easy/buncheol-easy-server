package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDomainService 단위 테스트")
class UserDomainServiceTest {

  @InjectMocks private UserDomainService userDomainService;

  @Mock private UserRepository userRepository;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

  @Nested
  @DisplayName("소셜 로그인 테스트")
  class GetOrCreateBySocialLoginTest {

    @Test
    void 기존_유저가_있으면_해당_유저를_반환한다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      User result = userDomainService.getOrCreateBySocialLogin(socialInfo, "test@example.com");

      // then
      assertThat(result).isEqualTo(existingUser);
      then(userRepository).should(never()).save(any());
    }

    @Test
    void 기존_유저가_없으면_새_유저를_생성하고_저장한다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "new_user");
      String email = "new@example.com";
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.empty());
      given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      User result = userDomainService.getOrCreateBySocialLogin(socialInfo, email);

      // then
      assertThat(result.getEmail().value()).isEqualTo(email);
      assertThat(result.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      then(userRepository).should().save(any(User.class));
    }
  }

  @Nested
  @DisplayName("유효한 User 검사 테스트")
  class IsValidUserTest {

    @Test
    void 유저가_존재하면_true를_반환한다() {
      // given
      given(userRepository.existsById(1L)).willReturn(true);

      // when
      boolean result = userDomainService.isValidUser(1L);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void 유저가_존재하지_않으면_false를_반환한다() {
      // given
      given(userRepository.existsById(999L)).willReturn(false);

      // when
      boolean result = userDomainService.isValidUser(999L);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("User 조회 테스트")
  class GetUserTest {

    @Test
    void 존재하는_유저를_조회할_수_있다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      User result = userDomainService.getUser(1L);

      // then
      assertThat(result).isEqualTo(user);
    }

    @Test
    void 존재하지_않는_유저를_조회하면_예외가_발생한다() {
      // given
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userDomainService.getUser(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("회원 탈퇴 테스트")
  class WithdrawTest {

    @Test
    void 회원_탈퇴_시_엔티티의_deletedAt이_설정된다() {
      // given: 더티체킹에 위임하므로 repository 의 명시적 update/withdraw 호출은 없음
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      userDomainService.withdraw(1L);

      // then
      assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_회원이_탈퇴하면_예외가_발생한다() {
      // given
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userDomainService.withdraw(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("User 프로필 업데이트 테스트")
  class UpdateProfileTest {

    @Test
    void 닉네임_중복이_없으면_엔티티에_변경사항이_반영된다() {
      // given: 더티체킹에 위임하므로 repository 의 명시적 update 호출은 없음
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678");

      // then
      assertThat(user.getNickname().value()).isEqualTo("새닉네임");
      assertThat(user.getPhoneNumber().value()).isEqualTo("01012345678");
    }

    @Test
    void 미완료_유저가_호출하면_프로필이_완료_상태로_전이된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      assertThat(user.isProfileCompleted()).isFalse();
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678");

      // then
      assertThat(user.isProfileCompleted()).isTrue();
    }

    @Test
    void 닉네임이_중복되면_예외가_발생하고_엔티티는_변경되지_않는다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      String originalNickname = user.getNickname().value();
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("중복닉네임", 1L)).willReturn(true);

      // when & then
      assertThatThrownBy(() -> userDomainService.updateProfile(1L, "중복닉네임", "01012345678"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NICKNAME_DUPLICATE);

      assertThat(user.getNickname().value()).isEqualTo(originalNickname);
      assertThat(user.getPhoneNumber()).isNull();
    }

    @Test
    void 존재하지_않는_유저의_프로필을_업데이트하면_예외가_발생한다() {
      // given
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userDomainService.updateProfile(999L, "새닉네임", "01012345678"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("정산 계좌 업데이트 테스트")
  class UpdateBankAccountTest {

    @Test
    void 기존_유저의_계좌를_갱신한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      userDomainService.updateBankAccount(1L, "국민은행", "123456", "홍길동");

      // then
      assertThat(user.getBankAccount()).isNotNull();
      assertThat(user.getBankAccount().bank()).isEqualTo("국민은행");
    }

    @Test
    void 존재하지_않는_유저의_계좌를_갱신하면_예외가_발생한다() {
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userDomainService.updateBankAccount(999L, "국민은행", "111", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("닉네임 중복 조회 테스트")
  class IsNicknameDuplicateTest {

    @Test
    void 다른_유저가_사용중이면_true를_반환한다() {
      given(userRepository.existsByNicknameExcludingId("중복닉", 1L)).willReturn(true);

      assertThat(userDomainService.isNicknameDuplicate("중복닉", 1L)).isTrue();
    }

    @Test
    void 사용중인_유저가_없으면_false를_반환한다() {
      given(userRepository.existsByNicknameExcludingId("새닉", 1L)).willReturn(false);

      assertThat(userDomainService.isNicknameDuplicate("새닉", 1L)).isFalse();
    }
  }

  @Nested
  @DisplayName("정산 계좌 등록 검증 테스트")
  class RequireBankAccountRegisteredTest {

    @Test
    void 계좌가_등록되어_있으면_통과한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateBankAccount("국민은행", "111", "홍길동");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      userDomainService.requireBankAccountRegistered(1L);
    }

    @Test
    void 계좌가_등록되어_있지_않으면_예외가_발생한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThatThrownBy(() -> userDomainService.requireBankAccountRegistered(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
  }

  @Nested
  @DisplayName("분철 개최 권한 검증 테스트")
  class RequireCanHostTest {

    @Test
    void 개최_권한이_있으면_통과한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.allowHosting();
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      userDomainService.requireCanHost(1L);
    }

    @Test
    void 개최_권한이_없으면_예외가_발생한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThatThrownBy(() -> userDomainService.requireCanHost(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_CANNOT_HOST);
    }
  }
}
