package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.AlimtalkButton;
import buncheoleasy.notification.domain.AlimtalkButtonType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AlimtalkButton} 을 알리고 {@code button_1} 파라미터 JSON 으로 직렬화한다. 스펙:
 * {@code {"button":[{"name":..,"linkType":..,"linkTypeName":..,"linkMo":..,"linkPc":..}]}}. DS(배송조회) 는
 * 링크 필드를 싣지 않는다.
 */
@Component
public class AligoButtonJsonWriter {

  private final ObjectMapper objectMapper;

  public AligoButtonJsonWriter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(final AlimtalkButton button) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", button.name());
    entry.put("linkType", button.type().name());
    entry.put("linkTypeName", button.type().displayName());
    if (button.type() == AlimtalkButtonType.WL) {
      entry.put("linkMo", button.mobileUrl());
      entry.put("linkPc", button.pcUrl());
    }
    return objectMapper.writeValueAsString(Map.of("button", List.of(entry)));
  }
}
