package buncheoleasy.notification.application;

import buncheoleasy.notification.domain.AlimtalkButton;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.notification.infrastructure.AligoAlimtalkClient;
import buncheoleasy.notification.infrastructure.AligoProperties;
import buncheoleasy.notification.infrastructure.AlimtalkSendRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 템플릿·수신자·변수를 받아 템플릿 코드 매핑 + 본문·버튼 변수 치환 후 알림톡을 발송한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlimtalkSender {

  private final AligoProperties properties;
  private final AligoAlimtalkClient client;

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

    String message = template.render(variables);
    AlimtalkButton button =
        template.button() == null ? null : template.button().render(variables);

    // 변수 누락으로 #{...} 토큰이 남으면 깨진 본문이 나가지 않도록 발송을 거른다.
    if (hasUnresolvedToken(message)
        || (button != null && hasUnresolvedToken(button.mobileUrl()))) {
      log.error("알림톡 미치환 변수가 남아 발송 건너뜀 - template={}", template);
      return;
    }

    client.send(
        new AlimtalkSendRequest(tplCode, receiverPhone, template.subject(), message, button));
  }

  private boolean hasUnresolvedToken(final String text) {
    return text != null && text.contains("#{");
  }
}
