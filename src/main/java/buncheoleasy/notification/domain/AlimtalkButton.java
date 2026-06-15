package buncheoleasy.notification.domain;

import java.util.Map;

/**
 * 알림톡 버튼. WL(웹링크) 은 모바일·PC URL 을 가지며(링크에 {@code #{변수}} 가 있으면 발송 시 치환), DS(배송조회) 는 카카오가 본문의 택배사·운송장으로
 * 조회하므로 URL 이 없다.
 */
public record AlimtalkButton(String name, AlimtalkButtonType type, String mobileUrl, String pcUrl) {

  public static AlimtalkButton webLink(final String name, final String url) {
    return new AlimtalkButton(name, AlimtalkButtonType.WL, url, url);
  }

  public static AlimtalkButton deliveryTracking(final String name) {
    return new AlimtalkButton(name, AlimtalkButtonType.DS, null, null);
  }

  public AlimtalkButton render(final Map<String, String> variables) {
    return new AlimtalkButton(
        name,
        type,
        AlimtalkPlaceholders.replace(mobileUrl, variables),
        AlimtalkPlaceholders.replace(pcUrl, variables));
  }
}
