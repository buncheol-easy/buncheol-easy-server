package buncheoleasy.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.notification.domain.AlimtalkButton;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AligoButtonJsonWriterTest {

  private final AligoButtonJsonWriter writer = new AligoButtonJsonWriter(new ObjectMapper());

  @Test
  @DisplayName("WL 버튼은 알리고 스펙대로 button 배열 + linkMo/linkPc 를 포함한다")
  void webLinkButtonJson() {
    AlimtalkButton button =
        AlimtalkButton.webLink("입금 정보 확인하기", "https://buncheoleasy.com/profile/bids");

    String json = writer.toJson(button);

    assertThat(json)
        .contains("\"button\":[")
        .contains("\"name\":\"입금 정보 확인하기\"")
        .contains("\"linkType\":\"WL\"")
        .contains("\"linkTypeName\":\"웹링크\"")
        .contains("\"linkMo\":\"https://buncheoleasy.com/profile/bids\"")
        .contains("\"linkPc\":\"https://buncheoleasy.com/profile/bids\"");
  }

  @Test
  @DisplayName("DS 버튼은 배송조회 타입만 싣고 링크 필드는 제외한다")
  void deliveryTrackingButtonJson() {
    AlimtalkButton button = AlimtalkButton.deliveryTracking("배송조회");

    String json = writer.toJson(button);

    assertThat(json)
        .contains("\"name\":\"배송조회\"")
        .contains("\"linkType\":\"DS\"")
        .contains("\"linkTypeName\":\"배송조회\"")
        .doesNotContain("linkMo")
        .doesNotContain("linkPc");
  }

  @Test
  @DisplayName("동적 링크 버튼은 #{분철아이디} 치환 후 직렬화된다")
  void rendersDynamicLinkBeforeSerialize() {
    AlimtalkButton rendered =
        AlimtalkButton.webLink("입금 확인하러 가기", "https://buncheoleasy.com/products/#{분철아이디}/manage")
            .render(Map.of("분철아이디", "42"));

    String json = writer.toJson(rendered);

    assertThat(json)
        .contains("\"linkMo\":\"https://buncheoleasy.com/products/42/manage\"")
        .doesNotContain("#{");
  }
}
