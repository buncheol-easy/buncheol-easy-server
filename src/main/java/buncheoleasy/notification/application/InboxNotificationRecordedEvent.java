package buncheoleasy.notification.application;

/**
 * 수신함에 알림 한 건이 저장됐다. 커밋 뒤 앱 푸시가 같은 제목·본문·경로로 나간다({@code PushNotificationListener}).
 *
 * @param linkPath 알림을 누르면 열 in-app 경로. 없으면 null
 */
public record InboxNotificationRecordedEvent(
    Long recipientId, String title, String description, String linkPath) {}
