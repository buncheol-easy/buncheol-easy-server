package buncheoleasy.push.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PushDevice 테스트")
class PushDeviceTest {

  private static final String TOKEN = "a1".repeat(32);

  @Nested
  @DisplayName("create")
  class CreateTest {

    @Test
    void 기기_정보를_그대로_담는다() {
      PushDevice device =
          PushDevice.create(7L, PushPlatform.IOS, TOKEN, PushEnvironment.PRODUCTION);

      assertThat(device.getUserId()).isEqualTo(7L);
      assertThat(device.getPlatform()).isEqualTo(PushPlatform.IOS);
      assertThat(device.getToken()).isEqualTo(TOKEN);
      assertThat(device.getEnvironment()).isEqualTo(PushEnvironment.PRODUCTION);
    }

    @Test
    void 토큰은_소문자로_모으고_앞뒤_공백을_뗀다() {
      PushDevice device =
          PushDevice.create(
              7L, PushPlatform.IOS, "  " + "A1".repeat(32) + " ", PushEnvironment.SANDBOX);

      assertThat(device.getToken()).isEqualTo(TOKEN);
    }

    @Test
    void 토큰은_hex_64자부터_200자까지_받는다() {
      assertThat(
              PushDevice.create(7L, PushPlatform.IOS, "f".repeat(200), PushEnvironment.SANDBOX)
                  .getToken())
          .hasSize(200);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          "abc",
          // 63자
          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          // hex 가 아닌 글자(g)
          "gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg",
          // 64자 사이에 공백
          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        })
    void 형식이_틀린_토큰은_거절한다(final String token) {
      assertThatThrownBy(
              () -> PushDevice.create(7L, PushPlatform.IOS, token, PushEnvironment.SANDBOX))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 토큰이_200자를_넘으면_거절한다() {
      assertThatThrownBy(
              () ->
                  PushDevice.create(
                      7L, PushPlatform.IOS, "f".repeat(201), PushEnvironment.SANDBOX))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void 주인_플랫폼_환경이_없으면_거절한다() {
      assertThatThrownBy(
              () -> PushDevice.create(null, PushPlatform.IOS, TOKEN, PushEnvironment.SANDBOX))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> PushDevice.create(7L, null, TOKEN, PushEnvironment.SANDBOX))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> PushDevice.create(7L, PushPlatform.IOS, TOKEN, null))
          .isInstanceOf(BusinessException.class);
    }
  }
}
