package buncheoleasy.notification.domain;

import java.util.Map;

/**
 * 알림톡 버튼. WL(웹링크) 은 모바일·PC URL 을 가지며(링크에 {@code #{변수}} 가 있으면 발송 시 치환), AC(채널 추가) 는 카카오가 처리하므로 URL 이 없다.
 * 발신 채널이 광고추가형(AD)으로 등록돼 모든 템플릿 버튼 1번에 채널 추가가 붙으므로, 발송 버튼도 등록본과 동일하게 채널 추가를 함께 싣는다.
 */
public record AlimtalkButton(String name, AlimtalkButtonType type, String mobileUrl, String pcUrl) {

  // 광고추가형 템플릿의 1번 버튼. 등록본의 이름("채널 추가")·타입(AC)과 정확히 일치해야 발송이 템플릿 매칭을 통과한다.
  public static AlimtalkButton channelAdd() {
    return new AlimtalkButton("채널 추가", AlimtalkButtonType.AC, null, null);
  }

  public static AlimtalkButton webLink(final String name, final String url) {
    return new AlimtalkButton(name, AlimtalkButtonType.WL, url, url);
  }

  public AlimtalkButton render(final Map<String, String> variables) {
    return new AlimtalkButton(
        name,
        type,
        AlimtalkPlaceholders.replace(mobileUrl, variables),
        AlimtalkPlaceholders.replace(pcUrl, variables));
  }
}
