package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("User 도메인 테스트")
class UserTest {

  @Nested
  @DisplayName("User 생성 테스트")
  class CreateUserTest {

    @Test
    void 유저_생성에_성공한다() {
      // given
      String provider = "KAKAO";
      String providerId = "123456";
      String email = "test@example.com";

      // when
      User user = User.create(provider, providerId, email);

      // then
      assertThat(user.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(user.getSocialInfo().providerId()).isEqualTo(providerId);
      assertThat(user.getEmail().value()).isEqualTo(email);
      assertThat(user.getNickname().value()).startsWith("Guest");
      assertThat(user.getPhoneNumber()).isNull();
      assertThat(user.getDeletedAt()).isNull();
    }
  }

  @Nested
  @DisplayName("ProviderId 검증 테스트")
  class ValidateProviderIdTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void providerId가_null이거나_빈_값인_경우_예외가_발생한다(String providerId) {
      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", providerId, "test@example.com"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_SOCIAL_ID_REQUIRED);
    }

    @Test
    void providerId가_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String providerId = "a".repeat(101);

      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", providerId, "test@example.com"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_SOCIAL_ID_LENGTH_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@123456", " 123456", "한글123", "1 23"})
    void providerId_형식이_유효하지_않은_경우_예외가_발생한다(String providerId) {
      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", providerId, "test@example.com"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_SOCIAL_ID_FORMAT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "789012", "aa123", "test-user_123"})
    void 올바른_형식의_providerId로_유저를_생성할_수_있다(String providerId) {
      // when & then
      assertThatCode(() -> User.create("KAKAO", providerId, "test@example.com"))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Email 검증 테스트")
  class ValidateEmailTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void email이_null이거나_빈_값인_경우_예외가_발생한다(String email) {
      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", "123456", email))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_EMAIL_REQUIRED);
    }

    @Test
    void email이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String email = "a".repeat(321);

      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", "123456", email))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_EMAIL_LENGTH_INVALID);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"testexample.com", "test@", "test@example", "test@example.c", "@example.com"})
    void email_형식이_유효하지_않은_경우_예외가_발생한다(String email) {
      // when & then
      assertThatThrownBy(() -> User.create("KAKAO", "123456", email))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_EMAIL_FORMAT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "test@example.com",
          "test.user@example.co.kr",
          "test+tag@sub.example.com",
          "test_user123@example-domain.org"
        })
    void 올바른_형식의_email로_유저를_생성할_수_있다(String email) {
      // when & then
      assertThatCode(() -> User.create("KAKAO", "123456", email)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("PhoneNumber 검증 테스트")
  class ValidatePhoneNumberTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void phoneNumber를_null혹은_빈_값으로_변경할_경우_예외가_발생한다(String phoneNumber) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatThrownBy(() -> user.updatePhoneNumber(phoneNumber))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PHONE_NUMBER_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"012345678", "012345678901"})
    void phoneNumber가_길이가_유효하지_않으면_예외가_발생한다(String phoneNumber) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatThrownBy(() -> user.updatePhoneNumber(phoneNumber))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0201234567", "1012345678", "00123456789", "a101234567", "0201234567a"})
    void phoneNumber_형식이_유효하지_않은_경우_예외가_발생한다(String phoneNumber) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatThrownBy(() -> user.updatePhoneNumber(phoneNumber))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PHONE_NUMBER_FORMAT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"01012345678", "01112345678", "01612345678", "01912345678", "0161234567"})
    void 올바른_형식의_phoneNumber로_업데이트할_수_있다(String phoneNumber) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatCode(() -> user.updatePhoneNumber(phoneNumber)).doesNotThrowAnyException();

      assertThat(user.getPhoneNumber().value()).isEqualTo(phoneNumber);
    }
  }

  @Nested
  @DisplayName("PhoneNumber 최초 설정 시 profileCompleted 테스트")
  class ProfileCompletedTest {

    @Test
    void 전화번호를_최초_설정하면_profileCompleted가_true가_된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      assertThat(user.getPhoneNumber()).isNull();
      assertThat(user.isProfileCompleted()).isFalse();

      // when
      user.updatePhoneNumber("01012345678");

      // then
      assertThat(user.isProfileCompleted()).isTrue();
    }

    @Test
    void 전화번호가_이미_설정된_경우_업데이트해도_profileCompleted는_유지된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updatePhoneNumber("01012345678");
      assertThat(user.isProfileCompleted()).isTrue();
      String newPhoneNumber = "01098765432";

      // when
      user.updatePhoneNumber(newPhoneNumber);

      // then
      assertThat(user.isProfileCompleted()).isTrue();
      assertThat(user.getPhoneNumber().value()).isEqualTo(newPhoneNumber);
    }
  }

  @Nested
  @DisplayName("Nickname 업데이트 테스트")
  class UpdateNicknameTest {

    @Test
    void 올바른_형식의_nickname으로_업데이트할_수_있다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      String newNickname = "새닉네임";

      // when & then
      assertThatCode(() -> user.updateNickname(newNickname)).doesNotThrowAnyException();

      assertThat(user.getNickname().value()).isEqualTo(newNickname);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nickname이_null이거나_빈_값인_경우_예외가_발생한다(String nickname) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatThrownBy(() -> user.updateNickname(nickname))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NICKNAME_REQUIRED);
    }

    @Test
    void nickname이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      String nickname = "a".repeat(21);

      // when & then
      assertThatThrownBy(() -> user.updateNickname(nickname))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NICKNAME_LENGTH_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"테스트@유저", "테스트 유저", "테스트_유저", "유저!", "nick#name"})
    void nickname_형식이_유효하지_않은_경우_예외가_발생한다(String nickname) {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");

      // when & then
      assertThatThrownBy(() -> user.updateNickname(nickname))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NICKNAME_FORMAT_INVALID);
    }
  }

  @Nested
  @DisplayName("User 탈퇴 테스트")
  class WithdrawTest {

    @Test
    void 탈퇴_시_deletedAt이_설정된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      assertThat(user.getDeletedAt()).isNull();

      // when
      user.withdraw(Instant.now());

      // then
      assertThat(user.getDeletedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("정산 계좌 등록/검증 테스트")
  class BankAccountTest {

    @Test
    void 신규_유저는_계좌가_없다() {
      User user = User.create("KAKAO", "123456", "test@example.com");

      assertThat(user.getBankAccount()).isNull();
    }

    @Test
    void 계좌를_등록하면_조회된다() {
      User user = User.create("KAKAO", "123456", "test@example.com");

      user.updateBankAccount("국민은행", "123456789012", "홍길동");

      assertThat(user.getBankAccount()).isNotNull();
      assertThat(user.getBankAccount().bank()).isEqualTo("국민은행");
      assertThat(user.getBankAccount().account()).isEqualTo("123456789012");
      assertThat(user.getBankAccount().holder()).isEqualTo("홍길동");
    }

    @Test
    void 계좌를_갱신하면_새_값으로_덮어쓴다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateBankAccount("국민은행", "111", "홍길동");

      user.updateBankAccount("신한은행", "222", "김철수");

      assertThat(user.getBankAccount().bank()).isEqualTo("신한은행");
      assertThat(user.getBankAccount().account()).isEqualTo("222");
      assertThat(user.getBankAccount().holder()).isEqualTo("김철수");
    }

    @Test
    void 계좌_미등록_상태에서_requireBankAccount_호출_시_예외가_발생한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");

      assertThatThrownBy(user::requireBankAccount)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }

    @Test
    void 계좌_등록_상태에서_requireBankAccount는_통과한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateBankAccount("국민은행", "111", "홍길동");

      assertThatCode(user::requireBankAccount).doesNotThrowAnyException();
    }
  }
}
