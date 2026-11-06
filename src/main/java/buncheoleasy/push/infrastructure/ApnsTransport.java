package buncheoleasy.push.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** 애플 서버에 HTTP/2 POST 한 번. 테스트에서 가짜로 바꿔 끼우려고 발송 판단({@link ApnsClient})과 나눴다. */
public interface ApnsTransport {

  ApnsResponse post(URI uri, Map<String, String> headers, String body)
      throws IOException, InterruptedException;

  record ApnsResponse(int status, String body) {}
}
