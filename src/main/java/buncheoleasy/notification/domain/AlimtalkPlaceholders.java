package buncheoleasy.notification.domain;

import java.util.Map;

/** {@code #{변수}} 치환 공용 로직. 본문과 버튼 링크에서 함께 쓴다. */
final class AlimtalkPlaceholders {

  private AlimtalkPlaceholders() {}

  static String replace(final String template, final Map<String, String> variables) {
    if (template == null) {
      return null;
    }
    String replaced = template;
    for (final Map.Entry<String, String> entry : variables.entrySet()) {
      replaced = replaced.replace("#{" + entry.getKey() + "}", entry.getValue());
    }
    return replaced;
  }
}
