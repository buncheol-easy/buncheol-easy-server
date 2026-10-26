package buncheoleasy.push.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.push.application.PushDeviceService;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushPlatform;
import buncheoleasy.push.dto.request.RegisterPushDeviceRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PushDeviceController 테스트")
class PushDeviceControllerTest {

  private static final Long USER_ID = 7L;
  private static final String TOKEN = "ab".repeat(32);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PushDeviceService pushDeviceService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  private static RequestPostProcessor user() {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private static String body(final String token, final String platform, final String environment) {
    return "{\"token\":%s,\"platform\":%s,\"environment\":%s}"
        .formatted(quote(token), quote(platform), quote(environment));
  }

  private static String quote(final String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  @Test
  void 기기를_등록하면_204_이고_로그인한_회원으로_넘긴다() throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(TOKEN, "IOS", "PRODUCTION")))
        .andExpect(status().isNoContent());

    ArgumentCaptor<RegisterPushDeviceRequest> captor =
        ArgumentCaptor.forClass(RegisterPushDeviceRequest.class);
    verify(pushDeviceService).register(eq(USER_ID), captor.capture());
    assertThat(captor.getValue().token()).isEqualTo(TOKEN);
    assertThat(captor.getValue().platform()).isEqualTo(PushPlatform.IOS);
    assertThat(captor.getValue().environment()).isEqualTo(PushEnvironment.PRODUCTION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "abc",
        // 63자
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg"
      })
  void 토큰_형식이_틀리면_400_이다(final String token) throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token, "IOS", "SANDBOX")))
        .andExpect(status().isBadRequest());

    verify(pushDeviceService, never()).register(any(), any());
  }

  @Test
  void 토큰이_200자를_넘으면_400_이다() throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("a".repeat(201), "IOS", "SANDBOX")))
        .andExpect(status().isBadRequest());

    verify(pushDeviceService, never()).register(any(), any());
  }

  @Test
  void 모르는_플랫폼_빠진_환경은_400_이다() throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(TOKEN, "ANDROID", "SANDBOX")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(TOKEN, "IOS", null)))
        .andExpect(status().isBadRequest());

    verify(pushDeviceService, never()).register(any(), any());
  }

  @Test
  void 기기를_해제하면_204_이고_로그인한_회원으로_넘긴다() throws Exception {
    mockMvc
        .perform(delete("/v1/push-devices/{token}", TOKEN).with(user()))
        .andExpect(status().isNoContent());

    verify(pushDeviceService).unregister(USER_ID, TOKEN);
  }

  @Test
  void 비로그인이면_등록도_해제도_401_이다() throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(TOKEN, "IOS", "SANDBOX")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(delete("/v1/push-devices/{token}", TOKEN))
        .andExpect(status().isUnauthorized());

    verify(pushDeviceService, never()).register(any(), any());
    verify(pushDeviceService, never()).unregister(any(), any());
  }
}
