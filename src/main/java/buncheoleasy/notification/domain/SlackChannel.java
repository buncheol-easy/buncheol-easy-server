package buncheoleasy.notification.domain;

/**
 * 슬랙 웹훅 발송 대상 채널. Incoming Webhook URL 은 채널당 1개이므로 채널 1개당 상수 1개 — 새 채널은 상수와 {@code
 * slack.webhook.urls} 설정(환경변수)을 함께 추가한다.
 */
public enum SlackChannel {

  /** 신규 참여 접수 알림 채널. 운영자가 입금 기한 내에 확인·입금확인해야 하는 참여 접수를 받는다. */
  NEW_PARTICIPATION
}
