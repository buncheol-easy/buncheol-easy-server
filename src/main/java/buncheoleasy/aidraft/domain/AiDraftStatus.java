package buncheoleasy.aidraft.domain;

/**
 * 요청 기록의 상태. 지금은 요청 스레드가 끝까지 기다리는 동기 처리라 RUNNING 은 엔진을 부르는 동안만 보인다 — 서버가 그 사이 죽으면 RUNNING 으로
 * 남는다(기록용이라 정리하지 않는다). 나중에 비동기로 바꾸면 이 칸이 작업 상태가 된다.
 */
public enum AiDraftStatus {
  RUNNING,
  SUCCEEDED,
  FAILED
}
