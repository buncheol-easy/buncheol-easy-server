package buncheoleasy.push.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushPlatform;
import buncheoleasy.push.dto.request.RegisterPushDeviceRequest;
import buncheoleasy.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushDeviceService 테스트")
class PushDeviceServiceTest {

  private static final Long USER_ID = 7L;
  private static final String TOKEN = "ab".repeat(32);

  @InjectMocks private PushDeviceService pushDeviceService;

  @Mock private PushDeviceRepository pushDeviceRepository;

  @Mock private UserRepository userRepository;

  @Nested
  @DisplayName("register")
  class RegisterTest {

    @Test
    void 소문자로_모은_토큰으로_upsert_하고_기기_수_상한을_넘는_행을_지운다() {
      given(userRepository.existsById(USER_ID)).willReturn(true);
      given(pushDeviceRepository.findIdsToTrim(USER_ID, PushDeviceService.MAX_DEVICES_PER_USER))
          .willReturn(List.of(3L));

      pushDeviceService.register(
          USER_ID,
          new RegisterPushDeviceRequest(
              TOKEN.toUpperCase(), PushPlatform.IOS, PushEnvironment.PRODUCTION));

      ArgumentCaptor<PushDevice> captor = ArgumentCaptor.forClass(PushDevice.class);
      then(pushDeviceRepository).should().upsert(captor.capture());
      assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
      assertThat(captor.getValue().getToken()).isEqualTo(TOKEN);
      assertThat(captor.getValue().getEnvironment()).isEqualTo(PushEnvironment.PRODUCTION);
      then(pushDeviceRepository).should().deleteAllByIdIn(List.of(3L));
    }

    @Test
    void 기기_수_상한은_10대다() {
      assertThat(PushDeviceService.MAX_DEVICES_PER_USER).isEqualTo(10);
    }

    @Test
    void 탈퇴했거나_없는_회원이면_USER_NOT_FOUND_이고_등록하지_않는다() {
      given(userRepository.existsById(USER_ID)).willReturn(false);

      assertThatThrownBy(
              () ->
                  pushDeviceService.register(
                      USER_ID,
                      new RegisterPushDeviceRequest(
                          TOKEN, PushPlatform.IOS, PushEnvironment.SANDBOX)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_NOT_FOUND);

      then(pushDeviceRepository).should(never()).upsert(any());
    }

    @Test
    void 토큰_형식이_틀리면_INVALID_INPUT_VALUE_이고_등록하지_않는다() {
      assertThatThrownBy(
              () ->
                  pushDeviceService.register(
                      USER_ID,
                      new RegisterPushDeviceRequest(
                          "not-a-token", PushPlatform.IOS, PushEnvironment.SANDBOX)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

      then(pushDeviceRepository).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("unregister · removeInvalidToken")
  class DeleteTest {

    @Test
    void 해제는_본인_조건을_걸고_소문자로_모은_토큰으로_지운다() {
      pushDeviceService.unregister(USER_ID, TOKEN.toUpperCase());

      then(pushDeviceRepository).should().deleteOwnedByToken(USER_ID, TOKEN);
    }

    @Test
    void 무효_토큰은_주인_조건_없이_지운다() {
      pushDeviceService.removeInvalidToken(TOKEN);

      then(pushDeviceRepository).should().deleteByToken(TOKEN);
    }
  }
}
