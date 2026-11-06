package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDomainService 단위 테스트")
class UserDomainServiceTest {

  @InjectMocks private UserDomainService userDomainService;

  @Mock private UserRepository userRepository;

  @Mock private buncheoleasy.user.domain.serviceterm.UserServiceTermRepository userServiceTermRepository;

  @Mock private RandomNicknameGenerator nicknameGenerator;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

  @Nested
  @DisplayName("소셜 로그인 테스트")
  class GetOrCreateBySocialLoginTest {

    @Test
    void 기존_유저가_있으면_해당_유저를_반환하고_카카오_값으로_덮어쓰지_않는다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      existingUser.updateName("김기존");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      SocialLoginUser loginUser =
          userDomainService.getOrCreateBySocialLogin(
              socialInfo, "test@example.com", "김카카오", "01099998888", null, false);

      // then
      User result = loginUser.user();
      assertThat(loginUser.newAccount()).isFalse();
      assertThat(result).isEqualTo(existingUser);
      assertThat(result.getName()).isEqualTo("김기존");
      assertThat(result.getPhoneNumber()).isNull();
      then(userRepository).should(never()).save(any());
    }

    @Test
    void 기존_유저도_연령대는_재로그인_때_최신값으로_갱신된다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      existingUser.updateAgeRange("15~19");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      User result =
          userDomainService.getOrCreateBySocialLogin(
              socialInfo, "test@example.com", null, null, "20~29", false).user();

      // then
      assertThat(result.getAgeRange()).isEqualTo("20~29");
    }

    @Test
    void 연령대가_null이면_기존_유저의_저장된_연령대를_지우지_않는다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      existingUser.updateAgeRange("20~29");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      User result =
          userDomainService.getOrCreateBySocialLogin(
              socialInfo, "test@example.com", null, null, null, false).user();

      // then
      assertThat(result.getAgeRange()).isEqualTo("20~29");
    }

    @Test
    void 연령대_동의_철회가_확인되면_저장된_연령대를_파기한다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      existingUser.updateAgeRange("20~29");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      User result =
          userDomainService.getOrCreateBySocialLogin(
              socialInfo, "test@example.com", null, null, null, true).user();

      // then
      assertThat(result.getAgeRange()).isNull();
    }

    @Test
    void 기존_유저가_없으면_조합_닉네임으로_새_유저를_생성하고_저장한다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "new_user");
      String email = "new@example.com";
      given(nicknameGenerator.generate()).willReturn("용감한까마귀12");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.empty());
      given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      SocialLoginUser loginUser =
          userDomainService.getOrCreateBySocialLogin(socialInfo, email, null, null, null, false);

      // then
      User result = loginUser.user();
      assertThat(loginUser.newAccount()).isTrue();
      assertThat(result.getEmail().value()).isEqualTo(email);
      assertThat(result.getSocialInfo().provider()).isEqualTo(SocialProvider.KAKAO);
      assertThat(result.getNickname().value()).isEqualTo("용감한까마귀12");
      assertThat(result.isProfileCompleted()).isFalse();
      then(userRepository).should().save(any(User.class));
    }

    @Test
    void 동의창에서_이름_전화번호_연령대를_받으면_완성_회원으로_생성된다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "sync_user");
      given(nicknameGenerator.generate()).willReturn("포근한수달7");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.empty());
      given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      User result =
          userDomainService.getOrCreateBySocialLogin(
              socialInfo, "sync@example.com", "김실명", "01012345678", "20~29", false).user();

      // then
      assertThat(result.getName()).isEqualTo("김실명");
      assertThat(result.getPhoneNumber().value()).isEqualTo("01012345678");
      assertThat(result.getAgeRange()).isEqualTo("20~29");
      assertThat(result.isProfileCompleted()).isTrue();
    }

    @Test
    void 기존_회원은_카카오가_이메일을_주지_않아도_로그인되고_저장된_이메일은_그대로다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      SocialLoginUser loginUser =
          userDomainService.getOrCreateBySocialLogin(socialInfo, null, null, null, null, false);

      // then
      assertThat(loginUser.newAccount()).isFalse();
      assertThat(loginUser.user()).isEqualTo(existingUser);
      assertThat(loginUser.user().getEmail().value()).isEqualTo("test@example.com");
      then(userRepository).should(never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 새_회원인데_이메일이_없으면_AUTH_003_이고_회원을_만들지_않는다(final String email) {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "new_user");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () ->
                  userDomainService.getOrCreateBySocialLogin(
                      socialInfo, email, "김실명", "01012345678", "20~29", false))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
      then(userRepository).should(never()).save(any());
      then(nicknameGenerator).should(never()).generate();
    }
  }

  @Nested
  @DisplayName("연령대 추가 동의 반영 테스트")
  class RefreshAgeRangeTest {

    @Test
    void 가입한_회원이면_연령대를_갱신하고_PRESENT_를_돌려준다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      AgeRangeRefreshResult result = userDomainService.refreshAgeRange(socialInfo, "20~29", false);

      // then
      assertThat(result).isEqualTo(AgeRangeRefreshResult.PRESENT);
      assertThat(existingUser.getAgeRange()).isEqualTo("20~29");
    }

    @Test
    void 저장_규칙에_맞지_않는_값이면_저장하지_않고_ABSENT_를_돌려준다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      AgeRangeRefreshResult result = userDomainService.refreshAgeRange(socialInfo, " ", false);

      // then
      assertThat(result).isEqualTo(AgeRangeRefreshResult.ABSENT);
      assertThat(existingUser.getAgeRange()).isNull();
    }

    @Test
    void 동의_철회가_확인되면_저장된_연령대를_파기한다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "123456");
      User existingUser = User.create("KAKAO", "123456", "test@example.com");
      existingUser.updateAgeRange("20~29");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.of(existingUser));

      // when
      AgeRangeRefreshResult result = userDomainService.refreshAgeRange(socialInfo, null, true);

      // then
      assertThat(result).isEqualTo(AgeRangeRefreshResult.ABSENT);
      assertThat(existingUser.getAgeRange()).isNull();
    }

    @Test
    void 가입한_회원이_없으면_새로_만들지_않고_NO_MEMBER_를_돌려준다() {
      // given
      SocialInfo socialInfo = SocialInfo.of("KAKAO", "unknown");
      given(userRepository.findBySocialInfo(socialInfo)).willReturn(Optional.empty());

      // when
      AgeRangeRefreshResult result = userDomainService.refreshAgeRange(socialInfo, "20~29", false);

      // then
      assertThat(result).isEqualTo(AgeRangeRefreshResult.NO_MEMBER);
      then(userRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("C2C 개최 자격 게이트 테스트")
  class RequireC2cHostQualificationTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private User phoneRegisteredUser() {
      return phoneRegisteredUser(SocialProvider.KAKAO);
    }

    private User phoneRegisteredUser(final SocialProvider provider) {
      User user = User.create(provider.name(), "123456", "test@example.com");
      user.updatePhoneNumber("01012345678");
      return user;
    }

    /** 개최 허용 목록 밖 가입 경로는 지금 SocialProvider 에 없어서, provider 를 비워 만든다. */
    private User outsideHostProvidersUser() {
      User user = phoneRegisteredUser();
      ReflectionTestUtils.setField(user, "socialInfo", new SocialInfo(null, "123456"));
      return user;
    }

    private void givenConfirmed(final Long id) {
      given(userServiceTermRepository.findByUserIdAndTag(id, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(
              Optional.of(
                  UserServiceTerm.of(
                      id, UserServiceTerm.HOST_ADULT_CONFIRM_TAG, true, CONFIRMED_AT)));
    }

    private void givenNotConfirmed(final Long id) {
      given(userServiceTermRepository.findByUserIdAndTag(id, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(
        value = SocialProvider.class,
        names = {"KAKAO", "APPLE"})
    void 카카오와_애플_가입자는_전화번호와_체크를_갖추면_통과한다(final SocialProvider provider) {
      given(userRepository.findById(1L)).willReturn(Optional.of(phoneRegisteredUser(provider)));
      givenConfirmed(1L);

      userDomainService.requireC2cHostQualification(1L);
      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.QUALIFIED);
    }

    // 허용 목록은 기본이 막힘이다. 제공자를 새로 붙이면 이 테스트가 깨져, 그 제공자에게 개최를 열지 C2C_HOST_PROVIDERS 에서 정하게 한다.
    @Test
    void 가입_경로가_늘면_개최_허용_여부를_다시_정해야_한다() {
      assertThat(SocialProvider.values())
          .as("제공자를 추가하면 UserDomainService.C2C_HOST_PROVIDERS 포함 여부를 정할 것")
          .containsExactlyInAnyOrder(SocialProvider.KAKAO, SocialProvider.APPLE);
    }

    @Test
    void 애플_가입자도_가입_미완료면_프로필_예외가_발생한다() {
      User user = User.create("APPLE", "001234.abc.0123", "relay@privaterelay.appleid.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThatThrownBy(() -> userDomainService.requireC2cHostQualification(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
    }

    @Test
    void 애플_가입자도_체크_기록이_없으면_USER_HOST_ADULT_CONFIRM_REQUIRED_예외가_발생한다() {
      given(userRepository.findById(1L))
          .willReturn(Optional.of(phoneRegisteredUser(SocialProvider.APPLE)));
      givenNotConfirmed(1L);

      assertThatThrownBy(() -> userDomainService.requireC2cHostQualification(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_HOST_ADULT_CONFIRM_REQUIRED);
    }

    @Test
    void 허용_목록_밖_가입_경로면_USER_HOST_KAKAO_ACCOUNT_REQUIRED_예외가_발생한다() {
      given(userRepository.findById(1L)).willReturn(Optional.of(outsideHostProvidersUser()));

      assertThatThrownBy(() -> userDomainService.requireC2cHostQualification(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_HOST_KAKAO_ACCOUNT_REQUIRED);
    }

    @Test
    void 가입_미완료면_프로필_예외가_발생한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThatThrownBy(() -> userDomainService.requireC2cHostQualification(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
    }

    @Test
    void 체크_기록이_없으면_USER_HOST_ADULT_CONFIRM_REQUIRED_예외가_발생한다() {
      given(userRepository.findById(1L)).willReturn(Optional.of(phoneRegisteredUser()));
      givenNotConfirmed(1L);

      assertThatThrownBy(() -> userDomainService.requireC2cHostQualification(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_HOST_ADULT_CONFIRM_REQUIRED);
    }

    @Test
    void 오류_코드와_상태는_화면과_맞춘_계약이다() {
      assertThat(ErrorCode.USER_HOST_ADULT_CONFIRM_REQUIRED.getCode()).isEqualTo("USR-036");
      assertThat(ErrorCode.USER_HOST_ADULT_CONFIRM_REQUIRED.getHttpStatus())
          .isEqualTo(HttpStatus.CONFLICT);
      assertThat(ErrorCode.USER_HOST_KAKAO_ACCOUNT_REQUIRED.getCode()).isEqualTo("USR-037");
      assertThat(ErrorCode.USER_HOST_KAKAO_ACCOUNT_REQUIRED.getHttpStatus())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 허용_목록_밖_가입_경로면_전화번호가_없어도_가입_경로_사유가_먼저다() {
      User user = outsideHostProvidersUser();
      ReflectionTestUtils.setField(user, "profileCompleted", false);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.KAKAO_ACCOUNT_REQUIRED);
    }

    @Test
    void 허용_목록_밖_가입_경로면_체크가_있어도_막힌다() {
      given(userRepository.findById(1L)).willReturn(Optional.of(outsideHostProvidersUser()));
      lenient()
          .when(
              userServiceTermRepository.findByUserIdAndTag(
                  1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .thenReturn(
              Optional.of(
                  UserServiceTerm.of(
                      1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG, true, CONFIRMED_AT)));

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.KAKAO_ACCOUNT_REQUIRED);
    }

    @Test
    void 가입_미완료면_체크가_없어도_전화번호_사유가_먼저다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.PHONE_REQUIRED);
      then(userServiceTermRepository).shouldHaveNoInteractions();
    }

    @Test
    void 동의_안_함_행은_체크로_치지_않는다() {
      given(userRepository.findById(1L)).willReturn(Optional.of(phoneRegisteredUser()));
      given(userServiceTermRepository.findByUserIdAndTag(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(
              Optional.of(
                  UserServiceTerm.of(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG, false, null)));

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.ADULT_CONFIRM_REQUIRED);
    }

    // 연령대를 다시 보면 15~19 구간(만 19세 포함)과 미동의 회원이 도로 막힌다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"15~19", "20~29"})
    void 체크가_있으면_연령대와_무관하게_통과한다(final String ageRange) {
      User user = phoneRegisteredUser();
      user.updateAgeRange(ageRange);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      givenConfirmed(1L);

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.QUALIFIED);
    }

    @Test
    void 성인_연령대가_있어도_체크가_없으면_막힌다() {
      User user = phoneRegisteredUser();
      user.updateAgeRange("20~29");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      givenNotConfirmed(1L);

      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.ADULT_CONFIRM_REQUIRED);
    }

    @Test
    void 사전_조회는_같은_판정을_예외_대신_값으로_돌려준다() {
      // given — 개최 폼 진입 차단(docs/53 Q-07)이 제출 게이트와 같은 판정을 쓰는지 확인한다.
      given(userRepository.findById(1L)).willReturn(Optional.of(phoneRegisteredUser()));
      givenNotConfirmed(1L);
      given(userRepository.findById(2L)).willReturn(Optional.of(phoneRegisteredUser()));
      givenConfirmed(2L);

      // when & then
      assertThat(userDomainService.evaluateC2cHostQualification(1L))
          .isEqualTo(C2cHostQualification.ADULT_CONFIRM_REQUIRED);
      assertThat(userDomainService.evaluateC2cHostQualification(2L))
          .isEqualTo(C2cHostQualification.QUALIFIED);
    }
  }

  @Nested
  @DisplayName("개최 만 19세 본인 체크 저장 테스트")
  class ConfirmHostAdultTest {

    private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");
    private static final Instant FIRST_CONFIRMED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private void givenUserExists() {
      given(userRepository.findById(1L))
          .willReturn(Optional.of(User.create("KAKAO", "123456", "test@example.com")));
    }

    @Test
    void 처음이면_지금_시각으로_체크를_남긴다() {
      givenUserExists();
      given(userServiceTermRepository.findByUserIdAndTag(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(Optional.empty());

      userDomainService.confirmHostAdult(1L);

      ArgumentCaptor<UserServiceTerm> saved = ArgumentCaptor.forClass(UserServiceTerm.class);
      then(userServiceTermRepository).should().saveIfAbsent(saved.capture());
      assertThat(saved.getValue().getUserId()).isEqualTo(1L);
      assertThat(saved.getValue().getTag()).isEqualTo("host_adult_confirm");
      assertThat(saved.getValue().isAgreed()).isTrue();
      assertThat(saved.getValue().getAgreedAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_체크했으면_첫_체크_시각을_덮지_않는다() {
      givenUserExists();
      UserServiceTerm existing =
          UserServiceTerm.of(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG, true, FIRST_CONFIRMED_AT);
      given(userServiceTermRepository.findByUserIdAndTag(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(Optional.of(existing));

      userDomainService.confirmHostAdult(1L);

      assertThat(existing.getAgreedAt()).isEqualTo(FIRST_CONFIRMED_AT);
      then(userServiceTermRepository).should(never()).save(any());
      then(userServiceTermRepository).should(never()).saveIfAbsent(any());
    }

    @Test
    void 동의_안_함_행이_있으면_체크로_바꾼다() {
      // 204 를 주고 행을 그대로 두면 사전 조회가 계속 체크 필요로 답해 화면이 못 빠져나온다.
      givenUserExists();
      UserServiceTerm existing =
          UserServiceTerm.of(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG, false, null);
      given(userServiceTermRepository.findByUserIdAndTag(1L, UserServiceTerm.HOST_ADULT_CONFIRM_TAG))
          .willReturn(Optional.of(existing));

      userDomainService.confirmHostAdult(1L);

      assertThat(existing.isAgreed()).isTrue();
      assertThat(existing.getAgreedAt()).isEqualTo(NOW);
      then(userServiceTermRepository).should().save(existing);
    }

    @Test
    void 없는_회원이면_USER_NOT_FOUND_예외가_발생하고_남기지_않는다() {
      given(userRepository.findById(1L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userDomainService.confirmHostAdult(1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);

      then(userServiceTermRepository).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("약관 동의 내역 갱신 테스트")
  class UpdateServiceTermAgreementsTest {

    @Test
    void 신규_태그는_저장하고_마케팅_태그는_수신_동의도_갱신한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userServiceTermRepository.findByUserIdAndTag(eq(1L), any()))
          .willReturn(Optional.empty());
      Instant agreedAt = Instant.parse("2026-07-23T00:00:00Z");

      // when
      userDomainService.updateServiceTermAgreements(
          1L,
          java.util.List.of(
              new buncheoleasy.user.domain.serviceterm.ServiceTermAgreement(
                  "service_terms", true, agreedAt),
              new buncheoleasy.user.domain.serviceterm.ServiceTermAgreement(
                  "marketing", true, agreedAt)),
          "marketing");

      // then
      then(userServiceTermRepository)
          .should(org.mockito.Mockito.times(2))
          .save(any(buncheoleasy.user.domain.serviceterm.UserServiceTerm.class));
      assertThat(user.getMarketingAgreedAt()).isEqualTo(agreedAt);
    }

    @Test
    void 이미_동의_상태의_재로그인은_최초_동의_시각을_보존한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      Instant firstAgreedAt = Instant.parse("2026-01-01T00:00:00Z");
      user.updateMarketingAgreement(true, firstAgreedAt);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userServiceTermRepository.findByUserIdAndTag(eq(1L), any()))
          .willReturn(Optional.empty());

      // when — 재로그인에서 카카오가 새로운 동의 시각을 내려줘도
      userDomainService.updateServiceTermAgreements(
          1L,
          java.util.List.of(
              new buncheoleasy.user.domain.serviceterm.ServiceTermAgreement(
                  "marketing", true, Instant.parse("2026-07-24T00:00:00Z"))),
          "marketing");

      // then — no-op 가드가 최초 동의 시각을 보존한다
      assertThat(user.getMarketingAgreedAt()).isEqualTo(firstAgreedAt);
    }

    @Test
    void 마케팅_태그의_동의_시각이_없으면_현재_시각으로_기록한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userServiceTermRepository.findByUserIdAndTag(eq(1L), any()))
          .willReturn(Optional.empty());

      // when
      userDomainService.updateServiceTermAgreements(
          1L,
          java.util.List.of(
              new buncheoleasy.user.domain.serviceterm.ServiceTermAgreement(
                  "marketing", true, null)),
          "marketing");

      // then
      assertThat(user.getMarketingAgreedAt()).isEqualTo(Instant.now(clock));
    }

    @Test
    void 기존_태그는_동의_상태를_갱신한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      buncheoleasy.user.domain.serviceterm.UserServiceTerm existing =
          buncheoleasy.user.domain.serviceterm.UserServiceTerm.of(
              1L, "service_terms", false, null);
      given(userServiceTermRepository.findByUserIdAndTag(1L, "service_terms"))
          .willReturn(Optional.of(existing));
      Instant agreedAt = Instant.parse("2026-07-23T00:00:00Z");

      // when
      userDomainService.updateServiceTermAgreements(
          1L,
          java.util.List.of(
              new buncheoleasy.user.domain.serviceterm.ServiceTermAgreement(
                  "service_terms", true, agreedAt)),
          "marketing");

      // then
      assertThat(existing.isAgreed()).isTrue();
      assertThat(existing.getAgreedAt()).isEqualTo(agreedAt);
      then(userServiceTermRepository).should(never()).save(any());
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
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, null);

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
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, null);

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
      assertThatThrownBy(
              () -> userDomainService.updateProfile(1L, "중복닉네임", "01012345678", null, null))
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
      assertThatThrownBy(
              () -> userDomainService.updateProfile(999L, "새닉네임", "01012345678", null, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 마케팅_동의가_true면_동의_일시가_기록된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, true);

      // then
      assertThat(user.getMarketingAgreedAt()).isEqualTo(Instant.now(clock));
    }

    @Test
    void 마케팅_동의가_false면_동의_일시가_제거된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateMarketingAgreement(true, Instant.parse("2026-01-01T00:00:00Z"));
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, false);

      // then
      assertThat(user.getMarketingAgreedAt()).isNull();
    }

    @Test
    void 마케팅_동의가_null이면_기존_동의_상태를_유지한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateMarketingAgreement(true, Instant.parse("2026-01-01T00:00:00Z"));
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, null);

      // then
      assertThat(user.getMarketingAgreedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void 실명이_전달되면_갱신된다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", "김실명", null);

      // then
      assertThat(user.getName()).isEqualTo("김실명");
    }

    @Test
    void 실명이_null이면_기존_실명을_유지한다() {
      // given
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.updateName("김기존");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByNicknameExcludingId("새닉네임", 1L)).willReturn(false);

      // when
      userDomainService.updateProfile(1L, "새닉네임", "01012345678", null, null);

      // then
      assertThat(user.getName()).isEqualTo("김기존");
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
      userDomainService.updateBankAccount(1L, "국민은행", "12345678", "홍길동");

      // then
      assertThat(user.getBankAccount()).isNotNull();
      assertThat(user.getBankAccount().bank()).isEqualTo("국민은행");
    }

    @Test
    void 존재하지_않는_유저의_계좌를_갱신하면_예외가_발생한다() {
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userDomainService.updateBankAccount(999L, "국민은행", "11112222", "홍길동"))
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
      user.updateBankAccount("국민은행", "11112222", "홍길동");
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
  @DisplayName("분철 개최 권한 조회 테스트")
  class CanHostTest {

    @Test
    void 개최_권한이_있으면_true를_반환한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      user.allowHosting();
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThat(userDomainService.canHost(1L)).isTrue();
    }

    @Test
    void 개최_권한이_없으면_false를_반환한다() {
      User user = User.create("KAKAO", "123456", "test@example.com");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      assertThat(userDomainService.canHost(1L)).isFalse();
    }
  }
}
