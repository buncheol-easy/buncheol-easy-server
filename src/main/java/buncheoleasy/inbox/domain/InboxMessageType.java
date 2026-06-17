package buncheoleasy.inbox.domain;

/** 수신함 메시지 종류. 공지(전체 대상)와 알림(특정 사용자 대상)을 구분한다. */
public enum InboxMessageType {
  NOTICE,
  NOTIFICATION
}
