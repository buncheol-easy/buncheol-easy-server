package buncheoleasy.aidraft.application;

import java.time.LocalDate;

/**
 * 서비스 전체 AI 개최글 하루 생성 수가 알림 기준에 닿았다. 막지 않는다 — "사용량이 너무 늘면 그때 다시 정한다"(2026-10-22 사용자 결정)의 그때를
 * 알려 주는 신호다.
 *
 * @param date 한국 날짜
 * @param total 그날 합계(= 알림 기준)
 */
public record AiDraftDailyTotalReachedEvent(LocalDate date, long total) {}
