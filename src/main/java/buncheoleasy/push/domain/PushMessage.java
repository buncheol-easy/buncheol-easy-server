package buncheoleasy.push.domain;

/**
 * 기기에 보낼 알림 한 건. 수신함 알림과 같은 제목·본문·이동 경로를 쓴다.
 *
 * @param linkPath 알림을 누르면 열 in-app 경로. 없으면 null
 */
public record PushMessage(String title, String body, String linkPath) {}
