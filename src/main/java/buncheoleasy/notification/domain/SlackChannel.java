package buncheoleasy.notification.domain;

/**
 * 슬랙 웹훅 발송 대상 채널. Incoming Webhook URL 은 채널당 1개이므로 채널 1개당 상수 1개 — 새 채널은 상수와 {@code
 * slack.webhook.urls} 설정(환경변수)을 함께 추가한다.
 */
public enum SlackChannel {

  /** 운영자 채널. 신규 참여 등 운영자가 즉시 확인·조치해야 하는 알림. */
  OPERATION
}
