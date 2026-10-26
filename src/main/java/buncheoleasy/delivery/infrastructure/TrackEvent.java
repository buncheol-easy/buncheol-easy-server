package buncheoleasy.delivery.infrastructure;

import java.time.Instant;

/**
 * Delivery Tracker 가 돌려준 운송장 추적 이벤트 하나.
 *
 * @param statusCode 추적 상태 코드 (예: AVAILABLE_FOR_PICKUP, DELIVERED). 코드 추가에 깨지지 않게 enum 이 아닌 문자열
 * @param statusName 택배사 원문 상태 문구 — 코드만으로 단계를 가를 수 없는 캐리어(cupost)의 판정용
 * @param time 이벤트 발생 시각. 없거나 형식이 다르면 null — 호출 측이 현재 시각으로 대체
 */
public record TrackEvent(String statusCode, String statusName, Instant time) {}
