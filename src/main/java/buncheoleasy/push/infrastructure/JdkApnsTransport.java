package buncheoleasy.push.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JDK HttpClient 로 APNs 를 부른다. APNs 는 HTTP/2 만 받는다 — 연결 하나를 재사용해 여러 기기로 보낸다. */
@Component
public class JdkApnsTransport implements ApnsTransport {

  private final HttpClient httpClient;
  private final Duration requestTimeout;

  public JdkApnsTransport(final ApnsProperties properties) {
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(properties.connectTimeout())
            .build();
    this.requestTimeout = properties.requestTimeout();
  }

  @Override
  public ApnsResponse post(final URI uri, final Map<String, String> headers, final String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    headers.forEach(request::header);
    HttpResponse<String> response =
        httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return new ApnsResponse(response.statusCode(), response.body());
  }
}
