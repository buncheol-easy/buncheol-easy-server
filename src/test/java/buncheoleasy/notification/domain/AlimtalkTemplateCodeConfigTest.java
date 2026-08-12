package buncheoleasy.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 템플릿 코드 설정 누락은 런타임에 {@code AlimtalkSender} 가 로그만 남기고 조용히 발송을 건너뛰어(과금 방지 가드) 배포 후에야 드러난다. 새 템플릿을
 * 추가하면서 설정 키를 빠뜨리는 실수를 빌드 단계에서 잡는다.
 */
@DisplayName("알림톡 템플릿 코드 설정")
class AlimtalkTemplateCodeConfigTest {

  @Test
  @DisplayName("모든 템플릿이 application.yaml 의 template-codes 에 키를 갖는다")
  void everyTemplateHasConfiguredCode() {
    assertThat(templateCodeKeys())
        .containsAll(Arrays.stream(AlimtalkTemplate.values()).map(Enum::name).toList());
  }

  @Test
  @DisplayName("template-codes 에 존재하지 않는 템플릿 키가 남아 있지 않다")
  void noOrphanTemplateCodeKey() {
    assertThat(Arrays.stream(AlimtalkTemplate.values()).map(Enum::name).toList())
        .containsAll(templateCodeKeys());
  }

  @SuppressWarnings("unchecked")
  private List<String> templateCodeKeys() {
    try (InputStream yaml =
        getClass().getClassLoader().getResourceAsStream("application.yaml")) {
      Map<String, Object> root = new Yaml().load(yaml);
      Map<String, Object> aligo = (Map<String, Object>) root.get("aligo");
      Map<String, Object> alimtalk = (Map<String, Object>) aligo.get("alimtalk");
      Map<String, Object> templateCodes = (Map<String, Object>) alimtalk.get("template-codes");
      return List.copyOf(templateCodes.keySet());
    } catch (final Exception e) {
      throw new IllegalStateException("application.yaml 의 template-codes 를 읽지 못했습니다", e);
    }
  }
}
