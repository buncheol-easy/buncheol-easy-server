package buncheoleasy.delivery.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.delivery.application.TrackingSyncService;
import buncheoleasy.delivery.dto.request.TrackingCallbackRequest;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 콜백 인증(토큰)과 202 응답 계약을 검증한다. 토큰 기대값은 application-test.yaml 의 test-webhook-token. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("DeliveryTrackerWebhookController 테스트")
class DeliveryTrackerWebhookControllerTest {

  private static final String CALLBACK_PATH = "/v1/deliveries/webhook/callback";
  private static final String VALID_TOKEN = "test-webhook-token";
  private static final String BODY =
      """
      {"carrierId": "kr.cvsnet", "trackingNumber": "TRACK123"}""";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TrackingSyncService trackingSyncService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void 토큰이_일치하면_202_와_함께_비동기_동기화를_시작한다() throws Exception {
    mockMvc
        .perform(
            post(CALLBACK_PATH)
                .queryParam("token", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isAccepted());

    then(trackingSyncService).should().syncAsync("kr.cvsnet", "TRACK123");
  }

  @Test
  void 토큰이_틀리면_401_로_거부한다() throws Exception {
    mockMvc
        .perform(
            post(CALLBACK_PATH)
                .queryParam("token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnauthorized());

    then(trackingSyncService).should(never()).syncAsync(anyString(), anyString());
  }

  @Test
  void 토큰이_없으면_401_로_거부한다() throws Exception {
    mockMvc
        .perform(post(CALLBACK_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized());

    then(trackingSyncService).should(never()).syncAsync(anyString(), anyString());
  }

  @Test
  void 검증_토큰_미설정_환경에서는_수신_자체를_거부한다() {
    // test yaml 은 토큰이 세팅돼 있어 MockMvc 로는 못 타는 분기 — 토큰 없는 프로퍼티로 직접 검증한다.
    DeliveryTrackerProperties tokenless =
        new DeliveryTrackerProperties(
            "https://api",
            "id",
            "secret",
            "http://cb",
            "",
            Duration.ofHours(48),
            Duration.ofSeconds(3),
            Duration.ofSeconds(5));
    DeliveryTrackerWebhookController controller =
        new DeliveryTrackerWebhookController(trackingSyncService, tokenless);

    ResponseEntity<Void> response =
        controller.receiveCallback("any-token", new TrackingCallbackRequest("kr.cvsnet", "T1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    then(trackingSyncService).should(never()).syncAsync(anyString(), anyString());
  }
}
