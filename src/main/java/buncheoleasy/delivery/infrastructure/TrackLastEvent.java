package buncheoleasy.delivery.infrastructure;

import java.time.Instant;

/**
 * Delivery Tracker 가 돌려준 운송장의 최신 추적 이벤트.
 *
 * @param statusCode 추적 상태 코드 (예: IN_TRANSIT, AVAILABLE_FOR_PICKUP, DELIVERED). Delivery Tracker 가
 *     코드를 추가해도 파싱이 깨지지 않도록 enum 이 아닌 문자열로 두고, 호출 측이 아는 코드만 매핑한다.
 * @param statusName 택배사 원문 상태 문구 (예: "점포도착", "수령완료"). 캐리어에 따라 정규화가 안 돼 코드가 UNKNOWN 으로 오는 경우
 *     (cupost, 2026-07 실측)의 폴백 매핑에 쓴다.
 * @param time 이벤트 발생 시각. 응답에 없거나 형식이 예상과 다르면 null — 호출 측이 현재 시각으로 대체한다.
 */
public record TrackLastEvent(String statusCode, String statusName, Instant time) {}
