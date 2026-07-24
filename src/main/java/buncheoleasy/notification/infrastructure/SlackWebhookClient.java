package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.SlackChannel;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 슬랙 Incoming Webhook 클라이언트. 채널별 웹훅 URL 로 텍스트 또는 Block Kit 메시지를 발송한다. */
@Slf4j
@Component
public class SlackWebhookClient {

  private final RestClient restClient;
  private final SlackProperties properties;

  public SlackWebhookClient(final SlackProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder().requestFactory(createRequestFactory(properties)).build();
  }

  /** 채널의 웹훅 URL 이 설정된 환경인지. 발송자가 미설정 환경에서 메시지 조립(DB 조회)까지 건너뛸 수 있게 노출한다. */
  public boolean isEnabled(final SlackChannel channel) {
    return properties.enabled(channel);
  }

  public void send(final SlackChannel channel, final String text) {
    send(channel, text, null);
  }

  /**
   * Block Kit 메시지 발송. {@code fallbackText} 는 blocks 를 렌더링하지 못하는 화면(푸시 미리보기 등)용 대체 텍스트다.
   * blocks 가 null 이면 텍스트 전용 메시지와 동일하게 동작한다.
   */
  public void send(
      final SlackChannel channel,
      final String fallbackText,
      final List<Map<String, Object>> blocks) {
    String url = properties.url(channel);
    // 로컬·테스트는 URL 을 비워 의도적으로 꺼두므로 정상 상태다(debug 로만 남긴다).
    if (url == null) {
      log.debug("슬랙 웹훅 URL 미설정 - 발송 건너뜀 - channel={}", channel);
      return;
    }
    try {
      restClient
          .post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new SlackMessage(fallbackText, blocks))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.error("슬랙 웹훅 발송 통신 오류 - channel={}", channel, e);
      throw new SlackSendException("슬랙 웹훅 발송 통신 오류", e);
    }
  }

  private SimpleClientHttpRequestFactory createRequestFactory(final SlackProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  // blocks 미사용(텍스트 전용) 메시지가 "blocks": null 을 싣지 않도록 NON_NULL 직렬화.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record SlackMessage(String text, List<Map<String, Object>> blocks) {}
}
