package buncheoleasy.notification.application;

import buncheoleasy.notification.domain.AlimtalkButton;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.notification.infrastructure.AligoAlimtalkClient;
import buncheoleasy.notification.infrastructure.AligoProperties;
import buncheoleasy.notification.infrastructure.AlimtalkSendRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 템플릿·수신자·변수를 받아 템플릿 코드 매핑 + 본문·버튼 변수 치환 후 알림톡을 발송한다. */
@Slf4j
@Component
public class AlimtalkSender {

  // 버튼 URL 의 #{baseUrl} 을 치환할 프론트 origin 변수 키.
  private static final String VARIABLE_BASE_URL = "baseUrl";

  private final AligoProperties properties;
  private final AligoAlimtalkClient client;
  // 알림톡 버튼이 가리키는 프론트 웹앱 origin (환경별 주입).
  private final String frontendBaseUrl;

  public AlimtalkSender(
      final AligoProperties properties,
      final AligoAlimtalkClient client,
      @Value("${app.frontend.base-url}") final String frontendBaseUrl) {
    this.properties = properties;
    this.client = client;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  public void send(
      final AlimtalkTemplate template,
      final String receiverPhone,
      final Map<String, String> variables) {
    Map<AlimtalkTemplate, String> codes = properties.templateCodes();
    String tplCode = codes == null ? null : codes.get(template);
    if (tplCode == null || tplCode.isBlank()) {
      log.error("알림톡 템플릿 코드 미설정 - template={}, 발송 건너뜀", template);
      return;
    }

    // 호출자 변수에 프론트 base 를 더해 버튼 URL 의 #{baseUrl} 까지 치환한다. (호출자 맵이 불변일 수 있어 복사)
    Map<String, String> renderVariables = new HashMap<>(variables);
    renderVariables.put(VARIABLE_BASE_URL, frontendBaseUrl);

    String message = template.render(renderVariables);
    List<AlimtalkButton> buttons =
        template.buttons().stream().map(button -> button.render(renderVariables)).toList();

    // 변수 누락으로 #{...} 토큰이 남으면 깨진 본문·버튼이 나가지 않도록 발송을 거른다.
    // (webLink 는 mobileUrl==pcUrl, AC 는 링크가 없으므로 mobileUrl 검사만으로 모든 버튼 링크를 커버한다.)
    if (hasUnresolvedToken(message)
        || buttons.stream().anyMatch(button -> hasUnresolvedToken(button.mobileUrl()))) {
      log.error("알림톡 미치환 변수가 남아 발송 건너뜀 - template={}", template);
      return;
    }

    client.send(
        new AlimtalkSendRequest(tplCode, receiverPhone, template.subject(), message, buttons));
  }

  private boolean hasUnresolvedToken(final String text) {
    return text != null && text.contains("#{");
  }
}
