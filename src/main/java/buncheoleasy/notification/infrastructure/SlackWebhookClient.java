package buncheoleasy.notification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 슬랙 Incoming Webhook 클라이언트. 운영자 채널로 텍스트 메시지를 발송한다. */
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

  public void send(final String text) {
    // 로컬·테스트는 URL 을 비워 의도적으로 꺼두므로 정상 상태다(debug 로만 남긴다).
    if (!properties.enabled()) {
      log.debug("슬랙 웹훅 URL 미설정 - 발송 건너뜀");
      return;
    }
    try {
      restClient
          .post()
          .uri(properties.url())
          .contentType(MediaType.APPLICATION_JSON)
          .body(new SlackMessage(text))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.error("슬랙 웹훅 발송 통신 오류", e);
      throw new SlackSendException("슬랙 웹훅 발송 통신 오류", e);
    }
  }

  private SimpleClientHttpRequestFactory createRequestFactory(final SlackProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  private record SlackMessage(String text) {}
}
