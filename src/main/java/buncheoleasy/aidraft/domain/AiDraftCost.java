package buncheoleasy.aidraft.domain;

import java.math.BigDecimal;

/**
 * 초안 한 건에 든 AI 비용 — 엔진이 호출마다 적은 장부를 합친 값이다.
 *
 * @param costUsd 비용 합계(USD). 엔진이 단가를 모르는 모델이 끼면 틀린 합계 대신 null 을 준다
 * @param inputTokens 입력 토큰 합계. 엔진이 하나도 적지 않았으면 null
 * @param outputTokens 출력 토큰 합계. 엔진이 하나도 적지 않았으면 null
 * @param aiCallCount AI 호출 횟수
 */
public record AiDraftCost(
    BigDecimal costUsd, Integer inputTokens, Integer outputTokens, int aiCallCount) {}
