package buncheoleasy.push.infrastructure;

import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushMessage;
import buncheoleasy.push.infrastructure.ApnsTransport.ApnsResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * APNs 발송기. 기기의 환경(SANDBOX/PRODUCTION)에 맞는 애플 서버로 알림 한 건을 보내고 결과를 {@link ApnsSendResult} 로 돌려준다.
 *
 * <p><b>예외를 위로 던지지 않는다</b> — 앱 푸시는 알림톡·수신함의 덤이라, 여기 실패가 그쪽에 번지면 안 된다. 설정(네 값)이 없거나 키를 읽지 못하면 꺼진
 * 채로 떠서 아무것도 보내지 않는다(기동 상태는 {@code IntegrationStatusReporter} 가 한 줄로 남긴다).
 */
@Slf4j
@Component
public class ApnsClient {

  static final String PRODUCTION_HOST = "api.push.apple.com";
  static final String SANDBOX_HOST = "api.sandbox.push.apple.com";

  /**
   * 본문 상한(코드포인트). 애플 알림 페이로드 상한은 4KB 다. 지금 알림 본문은 변수를 채워도 500자 안쪽이라 잘리지 않고 펼쳐 보기에서 다 보인다. 최악의 경우(본문
   * 500자 × 4바이트 + 제목 200자 + 경로 500자 + 뼈대)도 약 3.5KB 라 4KB 를 넘지 않는다.
   */
  static final int BODY_MAX_CODE_POINTS = 500;

  private static final String ELLIPSIS = "…";
  private static final String BAD_DEVICE_TOKEN = "BadDeviceToken";
  private static final String EXPIRED_PROVIDER_TOKEN = "ExpiredProviderToken";

  private final ApnsProperties properties;
  private final ApnsTransport transport;
  private final ObjectMapper objectMapper;
  private final ApnsProviderToken providerToken;

  public ApnsClient(
      final ApnsProperties properties,
      final ApnsTransport transport,
      final ObjectMapper objectMapper,
      final Clock clock) {
    this.properties = properties;
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.providerToken = createProviderToken(properties, clock);
  }

  public boolean isEnabled() {
    return providerToken != null;
  }

  public ApnsSendResult send(final PushDevice device, final PushMessage message) {
    if (providerToken == null) {
      return ApnsSendResult.DISABLED;
    }
    try {
      String authToken = providerToken.current();
      ApnsResponse response =
          transport.post(uriOf(device), headers(authToken), payload(message));
      return interpret(device, response, authToken);
    } catch (IOException e) {
      log.warn("앱 푸시 전송 오류 - deviceId={}, error={}", device.getId(), e.toString());
      return ApnsSendResult.FAILED;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("앱 푸시 전송 중 스레드 중단 - deviceId={}", device.getId());
      return ApnsSendResult.FAILED;
    } catch (RuntimeException e) {
      log.warn("앱 푸시 발송 실패 - deviceId={}", device.getId(), e);
      return ApnsSendResult.FAILED;
    }
  }

  private ApnsSendResult interpret(
      final PushDevice device, final ApnsResponse response, final String authToken) {
    int status = response.status();
    if (status == 200) {
      return ApnsSendResult.SENT;
    }
    String reason = reasonOf(response.body());
    // 410 은 앱 삭제·알림 해제 등으로 더 이상 쓰지 않는 토큰, BadDeviceToken 은 환경이 틀렸거나 망가진 토큰이다.
    if (status == 410 || status == 400 && BAD_DEVICE_TOKEN.equals(reason)) {
      log.info(
          "애플이 기기 토큰을 무효로 답함, 행 삭제 - deviceId={}, status={}, reason={}",
          device.getId(),
          status,
          reason);
      return ApnsSendResult.TOKEN_INVALID;
    }
    if (status == 403 && EXPIRED_PROVIDER_TOKEN.equals(reason)) {
      providerToken.discard(authToken);
    }
    log.warn(
        "앱 푸시 거절 - deviceId={}, status={}, reason={}", device.getId(), status, reason);
    return ApnsSendResult.FAILED;
  }

  private URI uriOf(final PushDevice device) {
    String host =
        device.getEnvironment() == PushEnvironment.PRODUCTION ? PRODUCTION_HOST : SANDBOX_HOST;
    return URI.create("https://" + host + "/3/device/" + device.getToken());
  }

  private Map<String, String> headers(final String authToken) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("authorization", "bearer " + authToken);
    headers.put("apns-topic", properties.topic());
    headers.put("apns-push-type", "alert");
    headers.put("apns-priority", "10");
    return headers;
  }

  String payload(final PushMessage message) {
    Map<String, Object> alert = new LinkedHashMap<>();
    alert.put("title", message.title());
    alert.put("body", truncate(message.body()));
    Map<String, Object> aps = new LinkedHashMap<>();
    aps.put("alert", alert);
    aps.put("sound", "default");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("aps", aps);
    if (message.linkPath() != null) {
      payload.put("linkPath", message.linkPath());
    }
    return objectMapper.writeValueAsString(payload);
  }

  // 코드포인트 기준으로 자른다 — 문자(char) 기준이면 이모지의 서로게이트 쌍이 반으로 갈려 깨진 글자가 나간다.
  static String truncate(final String body) {
    if (body == null || body.codePointCount(0, body.length()) <= BODY_MAX_CODE_POINTS) {
      return body;
    }
    return body.substring(0, body.offsetByCodePoints(0, BODY_MAX_CODE_POINTS)) + ELLIPSIS;
  }

  private String reasonOf(final String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, ApnsErrorBody.class).reason();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static ApnsProviderToken createProviderToken(
      final ApnsProperties properties, final Clock clock) {
    if (!properties.configured()) {
      return null;
    }
    try {
      PrivateKey key = ApnsProviderToken.parsePrivateKey(properties.privateKey());
      return new ApnsProviderToken(properties.teamId(), properties.keyId(), key, clock);
    } catch (GeneralSecurityException e) {
      // 키 내용이 섞일 수 있는 메시지·스택은 남기지 않는다.
      log.warn(
          "APNS_PRIVATE_KEY 를 읽지 못해 앱 푸시를 끈다 — .p8 파일 본문(PEM)인지 확인 - error={}",
          e.getClass().getSimpleName());
      return null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ApnsErrorBody(String reason) {}
}
