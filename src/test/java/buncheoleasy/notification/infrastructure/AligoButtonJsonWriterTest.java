package buncheoleasy.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.notification.domain.AlimtalkButton;
import java.util.List;
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

    String json = writer.toJson(List.of(button));

    assertThat(json)
        .contains("\"button\":[")
        .contains("\"name\":\"입금 정보 확인하기\"")
        .contains("\"linkType\":\"WL\"")
        .contains("\"linkTypeName\":\"웹링크\"")
        .contains("\"linkMo\":\"https://buncheoleasy.com/profile/bids\"")
        .contains("\"linkPc\":\"https://buncheoleasy.com/profile/bids\"");
  }

  @Test
  @DisplayName("AC(채널 추가) 버튼은 타입만 싣고 링크 필드는 제외한다")
  void channelAddButtonJson() {
    String json = writer.toJson(List.of(AlimtalkButton.channelAdd()));

    assertThat(json)
        .contains("\"name\":\"채널 추가\"")
        .contains("\"linkType\":\"AC\"")
        .contains("\"linkTypeName\":\"채널 추가\"")
        .doesNotContain("linkMo")
        .doesNotContain("linkPc");
  }

  @Test
  @DisplayName("동적 링크 버튼은 #{분철아이디} 치환 후 직렬화된다")
  void rendersDynamicLinkBeforeSerialize() {
    AlimtalkButton rendered =
        AlimtalkButton.webLink("입금 확인하러 가기", "https://buncheoleasy.com/products/#{분철아이디}/manage")
            .render(Map.of("분철아이디", "42"));

    String json = writer.toJson(List.of(rendered));

    assertThat(json)
        .contains("\"linkMo\":\"https://buncheoleasy.com/products/42/manage\"")
        .doesNotContain("#{");
  }

  @Test
  @DisplayName("채널 추가(AC) + 웹링크(WL) 는 등록본 순서대로 한 배열에 직렬화되고 AC 는 링크를 제외한다")
  void channelAddThenWebLinkJson() {
    String json =
        writer.toJson(
            List.of(
                AlimtalkButton.channelAdd(),
                AlimtalkButton.webLink("참여 내역 보러가기", "https://buncheoleasy.com/profile/bids")));

    int acIndex = json.indexOf("\"linkType\":\"AC\"");
    int wlIndex = json.indexOf("\"linkType\":\"WL\"");
    assertThat(acIndex).isPositive();
    assertThat(wlIndex).isGreaterThan(acIndex);
    assertThat(json)
        .contains("\"name\":\"채널 추가\"")
        .contains("\"linkTypeName\":\"채널 추가\"")
        .contains("\"name\":\"참여 내역 보러가기\"")
        .contains("\"linkMo\":\"https://buncheoleasy.com/profile/bids\"");
  }
}
