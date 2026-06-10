package buncheoleasy.notification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 알리고 카카오 알림톡 발송 클라이언트. {@code POST /akv10/alimtalk/send/} (application/x-www-form-urlencoded). */
@Slf4j
@Component
public class AligoAlimtalkClient {

  private static final String SEND_PATH = "/akv10/alimtalk/send/";
  private static final String FLAG_YES = "Y";
  private static final String FLAG_NO = "N";
  // 인증 실패 등 일부 실패는 양수 509 로 내려오므로 음수와 별도로 차단한다.
  private static final int FAILURE_CODE_AUTH = 509;

  private final RestClient restClient;
  private final AligoProperties properties;
  private final AligoButtonJsonWriter buttonJsonWriter;

  public AligoAlimtalkClient(
      final AligoProperties properties, final AligoButtonJsonWriter buttonJsonWriter) {
    this.properties = properties;
    this.buttonJsonWriter = buttonJsonWriter;
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(createRequestFactory(properties))
            .build();
  }

  /** 알림톡 단건 발송. {@code message} 가 승인 템플릿과 동일하다는 전제다(개행 불일치 시 알리고가 미발송 처리). */
  public void send(final AlimtalkSendRequest request) {
    try {
      AligoSendResponse response =
          restClient
              .post()
              .uri(SEND_PATH)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(toForm(request))
              .retrieve()
              .body(AligoSendResponse.class);

      verify(response, request);
    } catch (RestClientException e) {
      log.error("알림톡 발송 통신 오류 - tplCode={}", request.tplCode(), e);
      throw new AlimtalkSendException("알림톡 발송 통신 오류", e);
    }
  }

  private MultiValueMap<String, String> toForm(final AlimtalkSendRequest request) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("apikey", properties.apiKey());
    form.add("userid", properties.userId());
    form.add("senderkey", properties.senderKey());
    form.add("tpl_code", request.tplCode());
    form.add("sender", properties.sender());
    form.add("receiver_1", request.receiverPhone());
    form.add("subject_1", request.subject());
    form.add("message_1", request.message());
    if (request.button() != null) {
      form.add("button_1", buttonJsonWriter.toJson(request.button()));
    }
    form.add("failover", properties.failover() ? FLAG_YES : FLAG_NO);
    form.add("testMode", properties.testMode() ? FLAG_YES : FLAG_NO);
    return form;
  }

  // 알리고는 전송 실패도 HTTP 200 + 음수 code 로 응답하므로 본문 code 로 판정한다. code 0 이상이 성공.
  private void verify(final AligoSendResponse response, final AlimtalkSendRequest request) {
    if (response == null) {
      throw new AlimtalkSendException("알림톡 발송 응답이 비어 있습니다");
    }
    if (response.code() < 0 || response.code() == FAILURE_CODE_AUTH) {
      log.error(
          "알림톡 발송 실패 - tplCode={}, code={}, message={}",
          request.tplCode(),
          response.code(),
          response.message());
      throw new AlimtalkSendException("알림톡 발송 실패: " + response.message());
    }
    log.info("알림톡 발송 성공 - tplCode={}, code={}", request.tplCode(), response.code());
  }

  private SimpleClientHttpRequestFactory createRequestFactory(final AligoProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  private record AligoSendResponse(int code, String message) {}
}
